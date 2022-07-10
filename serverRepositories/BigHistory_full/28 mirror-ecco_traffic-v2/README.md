# Smart Mirror
This is our implementation of a Smart Mirror which is running a raspberry pi. The idea is based on [MagicMirror](https://github.com/MichMich/MagicMirror). 
The goal here is to create a "simpler" version of MagicMirror which can be used the same way as MagicMirror but probably
without Electron. Therefore, it is ment to run inside a browser.

The ultimate goal would be to have some kind of layout-builder for our Smart Mirror where the user can simply drag, drop 
and resize the plugins as the user likes. Moreover, this should also be used to configure the plugins like (provide API-Keys
user credentials, etc.). \
After the configuration is done the layout should be applied to the actual frontend of the Smart Mirror. Hence, the layout-builder
has to be running on the raspberry pi (maybe even in the same backend instance).

Another big goal would be to be able to integrate the community developed 3rd party modules from [MagicMirror](https://github.com/MichMich/MagicMirror).
This would ultimately result in the perfect Smart Mirror. 

## Implementation Details
Currently, the following features are planned:
* Integrate a traffic service (in our case we get the data from [Ö3](https://oe3.orf.at/)) which gives information about 
  the traffic.
* Show weather information ([OpenWeatherMap](https://openweathermap.org) is used).
* Display current events using the users Google-Calendar
* Show RSS-feeds

### Backend
The core of the backend is a Spring-Boot Application which is obviously written in Java. Besides the Spring-stuff _Jackson_ 
is used for creating JSON objects.

### Frontend
The frontend uses the following:
* Angular and TypeScript for developing
* It runs on port 4200
* It uses a proxy config that forwards all calls to the backend.

### Build
For building the project _Maven_ is used. The Smart Mirror consists of three _Maven Projects_
1. Smart-Mirror Project which is the root project and just referst to the following modules
2. Frontend Project which builds the angular app using the _frontend-maven-plugin_
3. Backend Project which ensures that the frontend project is build beforehand

After the build from the parent project is done (_mvn install_) a JAR file gets created 
which contains the entire application. Currently, it can be found in the [target](backend/target/) folder. 

### Running
To run the application write _mvn spring-boot:run_ inside the backend folder or directly start the generated jar file 
with _java --enable-preview -jar path/to/file.jar_. The application then runs on port 8080.\
The _--enable-preview_ flag is needed because the backend implementation uses some preview features.
