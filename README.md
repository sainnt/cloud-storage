# Cloud storage app
## Motivation
Goal of my project is to build efficient cloud storage environment. For these purposes  I've defined low-level  transport protocol.
Stumbling stone of developing worthy product  was to build user-friendly GUI.


![ezgif-2-c76d37dbdd](https://user-images.githubusercontent.com/85884762/156447974-6cf3a506-e02c-4aa8-9c82-6194a74c29c3.gif)



## Server
Network interaction is built with asynchronous event-driven framework __Netty__. Data is exchanged through __self defined byte protocol__. This decision was made to provide efficency and client platform independency.

### Tecnology stack 
_Java 17,  Netty,  Spring,  Hibernate,  MySql,  Lombock._

Many technologies were purposfully avoided. For example instead of using Spring Security, password encryption was implemented on byte level using SHA-256 algorithm.
### Architecture 
I've managed to keep balance between efficient low-level code and architecture flexibility.
For example:
* Building requests from bytes is encapsulated via __Builder pattern__.
* Requests are processed with __Chain-of-responsibility pattern__.
* File transfer operations are encapsulated via __Command pattern__. 

Despite working with byte level, __SOLID__ principles are followed.

### Navigation
Filesystem is provided via complex directory entity mapping. Folder sharing is defined.

## Client
Client application is built with Java 17 using __JavaFX__. Asynchronicity is established via using non-blocking __Netty__ framework for network exchange.

### GUI
User-friendliness of interface  is achived by __Drag and drop__ functionality, and using intuitively appealing context menus.
Filesystems are represented by __custom TreeView__. Multiple scenes are staged. Client is always indicated with network operations progress.

### Architecture
* Requests are designed with __Command pattern__, wich is efficient for queuing and  reverting  actions.
* When user expands local directory, __Observer__ is registered to monitor filesystem events.
## Demonstration
https://youtu.be/i5WjRu25MU0
