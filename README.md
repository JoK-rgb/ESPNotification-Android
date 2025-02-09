This is a Android app designed to establish a conenction with a BLE device and transfer all notification texts to this device. 
The main purpose for this App was to connect my phone to an ESP32-C6-Mini (should work with other ESP32 models). 
The NotificationService will get all incoming notifications of the phone and the BluetoothService will send these notifications to the connected device.
This comes in handy when designing your own smartwatch project.

After installing the app you will be asked to allow permissions for notifications on startup:
![NotificationPermissions](https://github.com/user-attachments/assets/ffa79d16-6b11-43c2-a4e3-5b1bcd225bc3)
Obviously you need to allow this.

After that you will be presented with the main view of the app:
![04_Screenshot_20250209_192210_NotificationListener](https://github.com/user-attachments/assets/4e7fc2dd-27dd-449d-a792-6b2a637588dd)

There are two buttons: one for scanning all bluetooth devices and connecting to a selceted one and one for stopping the service.
If you press the "connect to device" button for the first time you will prompts to allow bluetooth permissions that you have to accept.
You will see that just once after the first installation.
![10_Screenshot_20250209_192216_Permission controller](https://github.com/user-attachments/assets/f4c0bce3-71f8-492f-ae53-4f12a6ba67ad)
![05_Screenshot_20250209_192220_Permission controller](https://github.com/user-attachments/assets/637a4756-17ad-46cd-9add-3cc9a42d71c3)

After that you will see a list of all recognized bluetooth devices:
![07_Screenshot_20250209_192224_NotificationListener](https://github.com/user-attachments/assets/be1ad62d-e362-4885-ac70-75ed1a58446b)

You can click on the one you want to connect to and allow the connectiong to that device:
![08_Screenshot_20250209_192227_NotificationListener](https://github.com/user-attachments/assets/29728905-c5d2-445c-a42a-7fd6705e770b)

After that the main view will display that it is conncted to that device:
![09_Screenshot_20250209_192242_NotificationListener](https://github.com/user-attachments/assets/c3acaec2-e7bb-43de-9549-068770355e5e)

Now all notifications will be send to the connected device.
To see the notificiations on the ESP32 you need the companion Arduino code. You will find it here: [LINK TO ARDUINO CODE](https://github.com/JoK-rgb/ESPNotification-Arduino)

If you want to stop the service and stop the connection, you can press the stop service button. If you do so, you will see this:
![06_Screenshot_20250209_192248_NotificationListener](https://github.com/user-attachments/assets/4c23cbeb-21fb-48db-b1db-4b34b4d8e408)

The button changes to start service to reneble the service.
