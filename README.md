# Roundware® for Android

## Roundware Overview

Roundware is a flexible distributed framework which collects, stores, organizes
and re-presents audio content. Basically, it lets you collect audio from anyone
with a smartphone or web access, upload it to a central repository along with
its metadata and then filter it and play it back collectively in continuous
audio streams.

For more information about Roundware® functionality and projects that use the
platform, please see: [roundware.org](http://www.roundware.org "Roundware")

## Creating your own Roundware Android App

### Summary
Starting with a copy of the `starter-app` directory, your App wraps the
available Roundware App functionality allowing customization of existing Java
Classes and Resources such as drawables and XML.


### Getting Started

Prerequisites:
* A working Roundware Server installation.
* Knowledge of Android Mobile Application development with Android Studio

Setup the codebase:
```bash
# Clone the codebase
git clone https://github.com/roundware/roundware-android.git
cd roundware-android
# Copy "starter app" to "app"
cp -R app-starter app
cd app
# Make your copy into a git repository
git init
# Add the origin remote repository
git remote add origin <your-app-repo>
# Add all files and store them in the repo.
git add .
git commit -m "Initial commit of original app-starter"
git push origin master
```

Configure the project:

* Open the project in Android Studio
* Edit `app/src/main/AndroidManifest.xml` to set your namespace. Change:
  ```xml
  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="org.roundware.app_starter">
  ```

  To:
  ```xml
  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.rw">
  ```

* Edit `app/build.gradle` to set your namespace. Change:
  ```
  defaultConfig {
       applicationId "org.roundware.app_starter"
  ```

  To:
  ```
  defaultConfig {
       applicationId "com.example.rw"
  ```

* Edit `app/src/main/res/values/rwconfig.xml` to set your server and project ID.
* Edit `app/src/main/res/values/strings.xml` to set your application name.
* Edit `app/src/main/res/values/api_keys.xml` to set your Google Maps API Key.
* Run 'app' to try your new Roundware app with the provided configuration.

### To infinity and beyond

* Any files you copy from the `rwapp` and `rwservice` modules into your `app`
  project will override the original versions during project build.
* Add the new values if the XML file already exists.
* It should be possible to make the majority of needed changes without ever
  modifying a file outside of the `app` directory. If not, please file an issue
  on the issue queue.
