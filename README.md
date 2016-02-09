The Nuimo controller is an intuitive controller for your computer and connected smart devices. The Nuimo Android SDK helps you to easily integrate your Android apps with Nuimo controllers.

## Installation

##### Android project requirements

Your Android project must target at least API level 18. Earlier Android versions didn't support Bluetooth Low Energy. So make sure that `minSdkVersion` is set at least to 18 or higher in your `build.gradle` file.

##### Gradle dependency for the Nuimo library

[The Nuimo SDK library for Android is available via the jCenter repository](http://jcenter.bintray.com/com/senic/nuimo-android/). To include the Nuimo library in your Android app dependencies you need to add two dependency lines to your `build.gradle`:

```groovy
dependencies {
   ...
   compile "com.senic:nuimo-android:0.3.0"
   compile "org.jetbrains.kotlin:kotlin-stdlib:1.0.0-rc-1036"
}
```

If you're wondering why `kotlin-stdlib` is needed: The Nuimo library is written in the [Kotlin programming language](https://kotlinlang.org/) instead of Java. If you are already writing your Android apps in Kotlin then there's no need for this extra line.

## Usage

##### Basic usage

The Nuimo library makes it very easy to connect your Android apps with Nuimo controllers. It only takes three steps and a very few lines of code to discover your Nuimo and receive gesture events:

1. Add a `NuimoDiscoveryListener` to an instance of `NuimoDiscoveryManager` and call `startDiscovery()`. This will discover Nuimo controllers nearby.

2. Receive discovered controllers by implementing the listener method `onDiscoverNuimoController`. Here you can
    1. Add an event listener to the discovered controller
    2. Initiate the Bluetooth connection to the discovered controller by calling `connect()`

3. Implement `NuimoControllerListener`'s event method `onGestureEvent` to access user events performed with the Nuimo controller.

The following code example demonstrates how to discover, connect and receive gesture events from your Nuimo.

##### Example code

```java
public class MainActivity extends AppCompatActivity implements NuimoDiscoveryListener {
    NuimoDiscoveryManager nuimoDiscovery = new NuimoDiscoveryManager(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nuimoDiscovery.addDiscoveryListener(this);
        nuimoDiscovery.startDiscovery();
    }

    @Override
    public void onDiscoverNuimoController(@NotNull NuimoController nuimoController) {
        nuimoDiscovery.stopDiscovery();
        nuimoController.addControllerListener(new NuimoListener());
        nuimoController.connect();
    }

    class NuimoListener extends BaseNuimoControllerListener {
        @Override
        public void onGestureEvent(@NotNull NuimoGestureEvent event) {
            System.out.println("Received event: " + event.getGesture() + ": " + event.getValue());
        }
    }
}
```

##### A ready to checkout Android demo appl

We've provided a ready to checkout Android app that demonstrates discovering, connecting and receiving events from your Nuimo controllers. It also demonstrates how display fancy icons on its LED matrix. Simply clone the [Nuimo Android demo repository](https://github.com/getsenic/nuimo-android-demo) and open it in Android Studio.

## Advanced usage

The Nuimo library for Android is much more powerful than the use cases presented above. More details to follow here soon.

## Contact & Support

Have questions or suggestions? Drop us a mail at developers@senic.com. We'll be happy to hear from you.

## License

The NuimoSwift source code is available under the MIT License.
