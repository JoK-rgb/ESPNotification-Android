This is a Android app designed to establish a conenction with a BLE device and transfer all notification texts to this device. 
The main purpose for this App was to connect my phone to an ESP32-C6-Mini (should work with other ESP32 models). 
The NotificationService will get all incoming notifications of the phone and the BluetoothService will send these notifications to the connected device.
This comes in handy when designing your own smartwatch project.

After installing the app you will be asked to allow permissions for notifications on startup:

![NotificationPermissions](https://github.com/user-attachments/assets/591a1a3e-90c8-496c-9d29-fd16cc9eea36)

Obviously you need to allow this.

After that you will be presented with the main view of the app:

![04_Screenshot_20250209_192210_NotificationListener](https://github.com/user-attachments/assets/8e975c05-b8ea-4ff5-ac6b-e5014b910b79)


There are two buttons: one for scanning all bluetooth devices and connecting to a selceted one and one for stopping the service.
If you press the "connect to device" button for the first time you will prompts to allow bluetooth permissions that you have to accept.
You will see that just once after the first installation.

![10_Screenshot_20250209_192216_Permission controller](https://github.com/user-attachments/assets/86ba3350-e1f8-4f0f-a81e-c2dd4eb0a0cd)

![05_Screenshot_20250209_192220_Permission controller](https://github.com/user-attachments/assets/a875c791-5e57-472c-a9e8-b2981f2d448e)


After that you will see a list of all recognized bluetooth devices:

![07_Screenshot_20250209_192224_NotificationListener](https://github.com/user-attachments/assets/cf9f1de8-b9bf-4498-942a-ac314a1413dc)


You can click on the one you want to connect to and allow the connectiong to that device:

![08_Screenshot_20250209_192227_NotificationListener](https://github.com/user-attachments/assets/5130c361-95e8-42aa-91e6-ebd4b0fddd2c)


After that the main view will display that it is conncted to that device:

![09_Screenshot_20250209_192242_NotificationListener](https://github.com/user-attachments/assets/e08a1b2e-e000-40d9-91b7-bd22995e551a)


Now all notifications will be send to the connected device.
To see the notificiations on the ESP32 you need the companion Arduino code. You will find it here: 
[LINK TO ARDUINO CODE](https://github.com/JoK-rgb/ESPNotification-Arduino)

If you want to stop the service and stop the connection, you can press the stop service button. If you do so, you will see this:

![06_Screenshot_20250209_192248_NotificationListener](https://github.com/user-attachments/assets/7854ed4f-1f8e-42fc-b297-b60320799f04)


The button changes to start service to reneble the service.
