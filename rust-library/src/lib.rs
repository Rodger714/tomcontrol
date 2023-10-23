use std::error::Error;
use std::mem;

use btleplug::api::Manager;
use jni::JNIEnv;
use jni::sys::{jbyteArray, jclass, jlong, jlongArray, jsize};
use uuid::Uuid;

use crate::connector::{Connector, PeripheralIndex};

mod connector;
mod error;

fn rethrow(_env: &mut JNIEnv, err: Box<dyn Error>) {
    let _ = _env.throw_new("com/github/salaink/tomcontrol/dlab/NativeException", err.to_string());
}

fn to_connector(connector_ptr: jlong) -> &'static Connector {
    return unsafe { mem::transmute::<jlong, *mut Connector>(connector_ptr).as_mut() }.unwrap()
}

impl From<jlong> for PeripheralIndex {
    fn from(v: jlong) -> Self {
        return PeripheralIndex {
            0: v as u64
        };
    }
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_createConnector(mut _env: JNIEnv) -> jlong {
    let result = connector::Connector::create();
    if let Err(e) = result {
        rethrow(&mut _env, e);
        return 0;
    }
    let b = Box::new(result.unwrap());
    let ptr = Box::into_raw(b);
    return ptr as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_scan(mut _env: JNIEnv, _: jclass, connector_ptr: jlong) {
    let connector = to_connector(connector_ptr);
    let result = connector.scan();
    if result.is_err() {
        rethrow(&mut _env, result.unwrap_err());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_listen(mut _env: JNIEnv, _: jclass, connector_ptr: jlong) {
    let connector = to_connector(connector_ptr);
    connector.listen();
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_write(
    mut _env: JNIEnv,
    _: jclass,
    connector_ptr: jlong,
    peripheral_id: jlong,
    service_id_hi: jlong,
    service_id_lo: jlong,
    characteristic_id_hi: jlong,
    characteristic_id_lo: jlong,
    data: jni::objects::JByteArray
) {
    let connector = to_connector(connector_ptr);
    let result = connector.write(
        PeripheralIndex::from(peripheral_id),
        Uuid::from_u64_pair(service_id_hi as u64, service_id_lo as u64),
        Uuid::from_u64_pair(characteristic_id_hi as u64, characteristic_id_lo as u64),
        _env.convert_byte_array(&data).unwrap().as_slice()
    );
    if result.is_err() {
        rethrow(&mut _env, result.unwrap_err());
    }
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_read(
    mut _env: JNIEnv,
    _: jclass,
    connector_ptr: jlong,
    peripheral_id: jlong,
    service_id_hi: jlong,
    service_id_lo: jlong,
    characteristic_id_hi: jlong,
    characteristic_id_lo: jlong
) -> jbyteArray {
    let connector = to_connector(connector_ptr);
    let result = connector.read(
        PeripheralIndex::from(peripheral_id),
        Uuid::from_u64_pair(service_id_hi as u64, service_id_lo as u64),
        Uuid::from_u64_pair(characteristic_id_hi as u64, characteristic_id_lo as u64)
    );
    if result.is_err() {
        rethrow(&mut _env, result.unwrap_err());
        return std::ptr::null_mut();
    }
    return _env.byte_array_from_slice(result.unwrap().as_slice()).unwrap().as_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_github_salaink_tomcontrol_dlab_Native_listDevices(
    mut _env: JNIEnv,
    _: jclass,
    connector_ptr: jlong
) -> jlongArray {
    let connector = to_connector(connector_ptr);
    let devices = connector.list_devices();
    let mut buf = vec![];
    for p in devices {
        buf.push(p.0 as i64);
    }
    let mut arr = _env.new_long_array(buf.len() as jsize).unwrap();
    _env.set_long_array_region(&arr, 0, buf.as_slice()).unwrap();
    return arr.as_raw()
}
