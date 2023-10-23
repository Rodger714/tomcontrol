# tomcontrol

This is a toy integration software compatible with [The Buttplug Intimate Device Control Standard](https://buttplug-spec.docs.buttplug.io/docs/spec). It acts as a replacement or proxy for [Intiface Central](https://intiface.com/central): tomcontrol can either run standalone and only connect to the toys implemented in tomcontrol, or it can connect to a Intiface to expose toys supported by Intiface as well.

## Installation

1. **Install Java 17. Older versions will not work.** Newer versions are not tested.
2. Download the jar for your platform on the [releases page](https://github.com/salaink/tomcontrol/releases).
3. Run the jar. There is no additional installation step.

## Components

The components run mostly separately. You can configure them in any order.

### BPIO Server

The BPIO server provides the buttplug protocol to games. If you want to use tomcontrol from games, you must start the BPIO server. The default port is 12350 (as opposed to Intiface's default 12345).

### BPIO Client

The BPIO client can connect to a normal Intiface instance. Any toys connected to that Intiface instance will also be available through the tomcontrol server. This means that you can connect to tomcontrol from a game, and use both the toys connected to tomcontrol, and those connected to Intiface.

The BPIO Client is optional. It is not required for tomcontrol to function, you can run tomcontrol without Intiface.

### DG-Lab E-Stim

This module adds support for the [DG-Lab](https://dungeon-lab.com/) Coyote power box v2. 

**E-Stim can be dangerous and we are not liable for any injury or damage for any reason, including software bugs in tomcontrol. Never attach electrodes above the waist, and never remove electrodes while the device is in operation.**

To connect to the power box, your computer must support bluetooth.

#### Connection

Turn on the power box. The eyes should appear yellow. Press the Scan button in the tomcontrol software. The eyes of the power box should become white. It may take around 10 seconds or more for the device to actually show up in tomcontrol as the connection is established. 

#### Electrodes

Attach the electrodes to your body. For safety, only plug in the electrodes into the power box after they have been securely attached to your body. Connect the cable to the "A" output of the power box.

#### Calibration

Calibrate the shock power. Any shocks tomcontrol sends (e.g. from a game) will be in the configured power range. The default maximum of 200 is fairly low but should be noticeable depending on electrode location. The calibration window has four buttons each for the minimum and maximum power levels: 

- The "-" button will decrease the min/max level. 
- The "- ⚡" button will also decrease the level, and fire a shock at the new level. 
- The "⚡" button will fire a shock at the level. 
- The "+ ⚡" button will increase the level and fire a shock at the new level. 

It is not possible to increase the level without firing a shock. This is a safety feature to avoid high levels. 

There is an additional software limit around 750. This limit cannot be disabled.

If you do not notice any shocks during the calibration process, stop. There may be something wrong with the connection. You do not want to accidentally shock yourself with a high power value when the connection suddenly starts working.

#### Game Implementation

The power box is exposed through the BPIO server as a device called "DG-Lab E-Stim". It accepts a RawWriteCmd with the non-standard `shock` endpoint. The data of the command should be UTF-8 encoded JSON in this format:

```json
{
  "power": 0.75,
  "patterns": [
    {
      "pulseDurationMs": 10,
      "pauseDurationMs": 100,
      "amplitude": 10
    },
    {
      "pulseDurationMs": 5,
      "pauseDurationMs": 100,
      "amplitude": 5
    }
  ]
}
```

The power must be in the range 0 to 1 and is translated to a power level in the calibrated range. The patterns are played one after the other.

#### Technical Details

This information is taken from [this reddit post](https://www.reddit.com/r/estim/comments/uadthp/dg_lab_coyote_review_by_an_electronics_engineer/).

The calibrated power level corresponds to the generated output voltage. 

The power box will fire pulses of that voltage every 1ms. The duration of each pulse depends on the "amplitude" setting in the JSON sent by the game, and goes up to 160µs. Note that tomcontrol imposes a hard limit on the amplitude setting of 10 that cannot be turned off.

The "pulseDurationMs" controls how many of these short pulses are sent in a row. Setting it to 10 will send 10 pulses, 1ms apart.

The "pauseDurationMs" controls the pause after this pulse set.

