# AndroidThingsDoorbell
Android Things Doorbell example. IoT and Client part.

What project do:
1. When user presses a button photo is made and sent to Firebase FireStorage. Also record about call is added to Firebase FireStore;
2. While button is pressed LED is on;
3. While button is pressed doorbell ring sound is played cyclically.
4. Client app gets an update from Firebase FireStore in real time and displayes records in list.

To compile project:
1. Create a keystore file for project;
2. Create a local.properties file in root project;
3. Add next fields to config:
```gradle
store_path=<Path_To_Jks>
store_password=<Jks_Password>
key_alias_iot=<Your_Alies_For_IoT_Key>
key_password_iot=<Your_Password_For_IoT_Key>
key_alias_client=<Your_Alies_For_Client_Key>
key_password_client=<Your_Password_For_Client_Key>
```
4. Create a project in Firebase cloud;
5. Add 2 apps with packages to Firebase:
    * com.nsizintsev.doorbell - Client side;
    * com.nsizintsev.doorbell.iot - IoT side;
6. Configure Firestorage with rules:
```Firebase
service firebase.storage {
  match /b/{bucket}/o {
    match /{userId}/doorbell/{fileName} {
    		allow read: if request.auth.uid == userId;        
        allow write: if request.auth.uid == userId
        						 && request.resource.size < 8 * 1024 * 1024
                     && request.resource.contentType.matches('image/jpeg');
      }
  }
}
```
7. Configure Cloud Database Firestore with rules:
```Firebase
service cloud.firestore {
  match /databases/{database}/documents {
    match /doorbells/{doorbell} {
    	allow read, update, delete: if request.auth.uid != null
      														&& request.auth.uid == resource.data.uid;
      allow create: if request.auth.uid != null
      						  && request.auth.uid == request.resource.data.uid                   
                    && request.resource.data.keys().hasAll(['uid', 'date', 'file'])
             			  && request.resource.data.size() == 3
                    && request.resource.data.uid is string
                    && request.resource.data.date is timestamp
                    && request.resource.data.file is string;       
    }
  }
}
```
   * Add index for collection `doorbells`, field `uid` `ascending`, field `date` `descending`.  
  
  Board configration:
  * Connect camera, display and headset to your board.
  * Connect button (with ground) and a led.
  ![Doorbell board](https://i.imgur.com/gmOlPDK.jpg)
  * Open app_iot/com.nsizintsev.doorbell.iot.peripheral.controller/DoorbellController.kt and configure ports at line 70 and 72, by default they are `GPIO2_IO03` for button and `GPIO2_IO05` for led.
  
