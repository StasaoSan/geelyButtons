#### This is an APK companion works with signals which sends from ESP32, use BLE.

Find device - 'geelyController'

### Nowadays supported events:

        // fan buttons
        "EVT:FAN:+1" -> fan speed +1
        "EVT:FAN:-1" -> fan speed -1
        
        // temps from encoders rotation
        "EVT:TEMP_MAIN:+1" -> main temperature +1 step (0.5 celsisus degree)
        "EVT:TEMP_MAIN:-1" -> main temperature -1 step (0.5 celsisus degree)
        "EVT:TEMP_PASS:+1" -> passenger temperature +1 step (0.5 celsisus degree) 
        "EVT:TEMP_PASS:-1" -> passenger temperature -1 step (0.5 celsisus degree)

        // enc1 buttons
        "EVT:ENC1CLK" -> click - climate on/off
        "EVT:ENC1LONG" -> long click - dual on/off

        // enc2 buttons
        "EVT:REAR_DEFROST" -> click - defrost rear window
        "EVT:ELECTRIC_DEFROST" -> long click - defrost front window

If you want, you can easy add your event (int or float intent supported now) - need GeelyInputBridge API
- add new func to Action.kt
- add in BleListenerService variable and set action in onCharacteristicChanged

Feautures:
- Work with GIB
- Automatic find ESP device after restarting
- Automatic connect
- Overlay signal (green - esp is connectd, red - error)
- Change temperature main / passenger with correct logic
- Sync climate temps, or start DUAL climate mode
- Change fan speed
- On / off main clinate switch
- On / off front defrost
- On / off rear defrost

Planned:
[x] wheel heat
[x] driver seat heat / fan
[x] passenger seat heat / fan
[x] rear seat heat
[x] thunk button [??] intent not found in GIB
[x] [!!] add feedback for all actions, and send it to ESP
[x] add volume up / down actions
[x] add change drive modes [??]

[!!] - important
[??] - IDK, maybe not need