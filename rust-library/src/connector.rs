use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};

use btleplug::api::{Central, CentralEvent, Characteristic, Manager as _, Peripheral, ScanFilter, WriteType};
use btleplug::platform::{Adapter, Manager, PeripheralId};
use tokio;
use tokio::runtime::Runtime;
use tokio::sync::Mutex;
use uuid::Uuid;

use crate::error::Error;

pub struct Connector {
    runtime: Arc<Runtime>,
    manager: Manager,
    adapter: Adapter,
    managed_devices: Mutex<HashSet<PeripheralIndex>>,
    peripheral_ids: Mutex<HashMap<PeripheralIndex, PeripheralId>>,
    next_peripheral_id: AtomicU64
}

#[derive(Copy, Clone, Default, Eq, Hash, PartialEq)]
pub struct PeripheralIndex(pub u64);

impl Connector {
    pub fn create() -> Result<Connector, Box<dyn std::error::Error>> {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(1)
            .enable_all()
            .build()
            .unwrap();
        let manager_res = runtime.block_on(Manager::new());
        if manager_res.is_err() {
            runtime.shutdown_background();
            return Err(Box::new(manager_res.unwrap_err()));
        }
        let manager = manager_res.unwrap();
        let adapters_res = runtime.block_on(manager.adapters());
        if adapters_res.is_err() {
            runtime.shutdown_background();
            return Err(Box::new(adapters_res.unwrap_err()));
        }
        let adapters = adapters_res.unwrap();
        if adapters.len() != 1 {
            runtime.shutdown_background();
            return Err(Box::new(Error::WrongAdapterCount));
        }
        let adapter = adapters.into_iter().nth(0).unwrap();
        return Ok(Connector {
            runtime: Arc::new(runtime),
            manager,
            adapter,
            managed_devices: Mutex::new(HashSet::new()),
            peripheral_ids: Mutex::new(HashMap::new()),
            next_peripheral_id: AtomicU64::new(0)
        });
    }

    pub fn scan(&self) -> Result<(), Box<dyn std::error::Error>> {
        let rt = self.runtime.clone();
        return rt.block_on(self.scan_async());
    }

    async fn scan_async(&self) -> Result<(), Box<dyn std::error::Error>> {
        self.adapter.start_scan(ScanFilter::default()).await?;
        return Ok(());
    }

    pub fn listen(&self) {
        let arc = self.runtime.clone();
        arc.block_on(self.listen_async());
    }

    async fn listen_async(&self) {
        use futures::stream::StreamExt;
        let mut events = self.adapter.events().await.unwrap();
        while let Some(v) = events.next().await {
            match v {
                CentralEvent::DeviceDiscovered(p) => {
                    let _ = self.device_discovered(p).await;
                }
                CentralEvent::DeviceUpdated(_) => {}
                CentralEvent::DeviceConnected(_) => {}
                CentralEvent::DeviceDisconnected(_) => {}
                CentralEvent::ManufacturerDataAdvertisement { .. } => {}
                CentralEvent::ServiceDataAdvertisement { .. } => {}
                CentralEvent::ServicesAdvertisement { .. } => {}
            }
        }
    }

    async fn device_discovered(&self, id: PeripheralId) -> Result<(), Box<dyn std::error::Error>> {
        let peripheral = self.adapter.peripheral(&id).await?;
        let properties = peripheral.properties().await?;
        if let Some(props) = properties {
            if let Some(name) = props.local_name {
                if name.starts_with("D-LAB ESTIM") {
                    let index = PeripheralIndex(self.next_peripheral_id.fetch_add(1, Ordering::AcqRel));
                    {
                        self.peripheral_ids.lock().await.insert(index, id);
                    }
                    println!("Connecting...");
                    peripheral.connect().await?;
                    println!("Discovering...");
                    peripheral.discover_services().await?;
                    println!("Ready!");
                    self.managed_devices.lock().await.insert(index);
                }
            }
        }
        return Ok(())
    }

    async fn find_characteristic(
        peripheral: &impl Peripheral,
        service_id: Uuid,
        characteristic_id: Uuid,
    ) -> Result<Characteristic, Error> {
        for characteristic in peripheral.characteristics() {
            //println!("service {} char {}", characteristic.service_uuid, characteristic.uuid);
            if characteristic.service_uuid == service_id &&
                characteristic.uuid == characteristic_id {
                return Ok(characteristic)
            }
        }
        return Err(Error::NoSuchCharacteristic)
    }

    async fn lookup_peripheral_id(&self, index: PeripheralIndex) -> Result<PeripheralId, Error> {
        match self.peripheral_ids.lock().await.get(&index) {
            None => Err(Error::NoSuchPeripheral),
            Some(id) => return Ok(id.clone())
        }
    }

    pub fn write(
        &self,
        peri_id: PeripheralIndex,
        service_id: Uuid,
        characteristic_id: Uuid,
        data: &[u8]
    ) -> Result<(), Box<dyn std::error::Error>> {
        let arc = self.runtime.clone();
        return arc.block_on(self.write_async(peri_id, service_id, characteristic_id, data))
    }

    async fn write_async(
        &self,
        peri_id: PeripheralIndex,
        service_id: Uuid,
        characteristic_id: Uuid,
        data: &[u8]
    ) -> Result<(), Box<dyn std::error::Error>> {
        let peripheral = self.adapter.peripheral(&self.lookup_peripheral_id(peri_id).await?).await?;
        let characteristic = Connector::find_characteristic(&peripheral, service_id, characteristic_id).await?;
        peripheral.write(&characteristic, data, WriteType::WithoutResponse).await?;
        return Ok(())
    }

    pub fn read(
        &self,
        peri_id: PeripheralIndex,
        service_id: Uuid,
        characteristic_id: Uuid
    ) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let arc = self.runtime.clone();
        return arc.block_on(self.read_async(peri_id, service_id, characteristic_id))
    }

    async fn read_async(
        &self,
        peri_id: PeripheralIndex,
        service_id: Uuid,
        characteristic_id: Uuid
    ) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let peripheral = self.adapter.peripheral(&self.lookup_peripheral_id(peri_id).await?).await?;
        let characteristic = Connector::find_characteristic(&peripheral, service_id, characteristic_id).await?;
        return Ok(peripheral.read(&characteristic).await?);
    }

    pub fn list_devices(&self) -> HashSet<PeripheralIndex> {
        return self.runtime.block_on(async { self.managed_devices.lock().await.clone() })
    }
}