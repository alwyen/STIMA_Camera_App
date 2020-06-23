package com.test.alexy.lightcapture;
//Settings --> Developer options --> USB debugging

import android.Manifest;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity {

//GPS location
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean repeat; //continue to take gps data or not

    private LocationListener locationListener;
    private LocationManager locationManager;
    private double latitude;
    private double longitude;


    private Boolean displayGpsStatus() {
        ContentResolver contentResolver = getBaseContext()
                .getContentResolver();
        boolean gpsStatus = Settings.Secure
                .isLocationProviderEnabled(contentResolver,
                        LocationManager.GPS_PROVIDER);
        if (gpsStatus) {
            return true;
        }
        else {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void saveGPSLocation(String fileName){
        String name = Environment.getExternalStorageDirectory()+"/Repeating_Burst_Test_GPS_Location_Files";
        File folder = new File(name);
        folder.mkdir();
        try {
            File file = new File(name + "/" + fileName + ".txt");
            PrintWriter textFile = new PrintWriter(file);
            textFile.println(latitude + ";" + longitude);
            textFile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void getGPSLocation(){
        //request permission
        ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.INTERNET}, 1);

        //check permission
        if (ActivityCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.INTERNET}
                        ,10);
            }
            return;
        }
        else{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//END

    //declare buttons
    private Button startCapture;
    private Button normalPicture;
    private Button rollingImage;
    private Button dcImage;

    //create textureView to display live camera image on smartphone
    private TextureView textureView;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //declare variables
    private String cameraID;
    private CameraDevice cameraDevice;
    private Size imageDimension;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Thread repeatingCaptureThread;

    private List<String> fileNameList;
    private List<File> fileList;
    private int numPictures;
    private int numTextFiles;
    private File file;
    private static final int requestPermission = 200;

    //phone host variables
    private static final int targetVendorID = 9025; //Arduino Uno; hex: 0x2341
    private static final int targetProductID = 67; //Arduino Uno, not 0067
    UsbDevice deviceFound = null;
    UsbInterface usbInterfaceFound = null;
    UsbEndpoint endpointIn = null;
    UsbEndpoint endpointOut = null;

    //USB permission
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    PendingIntent mPermissionIntent;
    UsbInterface usbInterface;
    UsbDeviceConnection usbDeviceConnection;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera; //get the current state of the camera
            createCameraPreview();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            if(cameraDevice != null){
                cameraDevice.close();
                cameraDevice = null;
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice = null; //reset
        }
    };


    //setting up usb host connection protocol methods
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onDestroy() {
        releaseUsb();
        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mUsbDeviceReceiver);
        super.onDestroy();
    }

    private void connectUsb() {

        Toast.makeText(CameraActivity.this, "connectUsb()", Toast.LENGTH_LONG).show();

        searchEndPoint();

        if (usbInterfaceFound != null) {
            setupUsbComm();
        }

    }

    private void releaseUsb() {

        Toast.makeText(CameraActivity.this, "releaseUsb()", Toast.LENGTH_LONG).show();

        if (usbDeviceConnection != null) {
            if (usbInterface != null) {
                usbDeviceConnection.releaseInterface(usbInterface);
                usbInterface = null;
            }
            usbDeviceConnection.close();
            usbDeviceConnection = null;
        }

        deviceFound = null;
        usbInterfaceFound = null;
        endpointIn = null;
        endpointOut = null;
    }

    private void searchEndPoint() {

        usbInterfaceFound = null;
        endpointOut = null;
        endpointIn = null;

        // Search device for targetVendorID and targetProductID
        if (deviceFound == null) {
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();

                if (device.getVendorId() == targetVendorID) {
                    if (device.getProductId() == targetProductID) {
                        deviceFound = device;
                    }
                }
            }
        }

        if (deviceFound == null) {
            //for usb connection
            Toast.makeText(CameraActivity.this, "device not found", Toast.LENGTH_LONG).show();
        } else {
            // Search for UsbInterface with Endpoint of USB_ENDPOINT_XFER_BULK,
            // and direction USB_DIR_OUT and USB_DIR_IN

            for (int i = 0; i < deviceFound.getInterfaceCount(); i++) {
                UsbInterface usbif = deviceFound.getInterface(i);

                UsbEndpoint tOut = null;
                UsbEndpoint tIn = null;

                int tEndpointCnt = usbif.getEndpointCount();
                if (tEndpointCnt >= 2) {
                    for (int j = 0; j < tEndpointCnt; j++) {
                        if (usbif.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT) {
                                tOut = usbif.getEndpoint(j);
                            } else if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN) {
                                tIn = usbif.getEndpoint(j);
                            }
                        }
                    }

                    if (tOut != null && tIn != null) {
                        // This interface have both USB_DIR_OUT
                        // and USB_DIR_IN of USB_ENDPOINT_XFER_BULK
                        usbInterfaceFound = usbif;
                        endpointOut = tOut;
                        endpointIn = tIn;
                    }
                }

            }

        }
    }

    private boolean setupUsbComm() {

        // for more info, search SET_LINE_CODING and
        // SET_CONTROL_LINE_STATE in the document:
        // "Universal Serial Bus Class Definitions for Communication Devices"
        // at http://adf.ly/dppFt
        final int RQSID_SET_LINE_CODING = 0x20;
        final int RQSID_SET_CONTROL_LINE_STATE = 0x22;

        boolean success = false;

        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Boolean permitToRead = manager.hasPermission(deviceFound);

        if (permitToRead) {
            usbDeviceConnection = manager.openDevice(deviceFound);
            if (usbDeviceConnection != null) {
                usbDeviceConnection.claimInterface(usbInterfaceFound, true);

                int usbResult;
                usbResult = usbDeviceConnection.controlTransfer(0x21, // requestType
                        RQSID_SET_CONTROL_LINE_STATE, // SET_CONTROL_LINE_STATE
                        0, // value
                        0, // index
                        null, // buffer
                        0, // length
                        0); // timeout

                Toast.makeText(CameraActivity.this, "controlTransfer(SET_CONTROL_LINE_STATE): " + usbResult, Toast.LENGTH_LONG).show();

                // baud rate = 9600
                // 8 data bit
                // 1 stop bit
                byte[] encodingSetting = new byte[]{(byte) 0x80, 0x25, 0x00,
                        0x00, 0x00, 0x00, 0x08};
                usbResult = usbDeviceConnection.controlTransfer(0x21, // requestType
                        RQSID_SET_LINE_CODING, // SET_LINE_CODING
                        0, // value
                        0, // index
                        encodingSetting, // buffer
                        7, // length
                        0); // timeout
                Toast.makeText(CameraActivity.this, "controlTransfer(RQSID_SET_LINE_CODING): " + usbResult, Toast.LENGTH_LONG).show();
            }

        } else {
            manager.requestPermission(deviceFound, mPermissionIntent);
            Toast.makeText(CameraActivity.this, "Permission: " + permitToRead, Toast.LENGTH_LONG).show();
        }

        return success;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {

                Toast.makeText(CameraActivity.this, "ACTION_USB_PERMISSION", Toast.LENGTH_LONG).show();

                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectUsb();
                        }
                    } else {
                        Toast.makeText(CameraActivity.this, "permission denied for device " + device, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

                deviceFound = (UsbDevice) intent
                        .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Toast.makeText(CameraActivity.this, "ACTION_USB_DEVICE_ATTACHED: \n" + deviceFound.toString(), Toast.LENGTH_LONG).show();

                connectUsb();

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Toast.makeText(CameraActivity.this, "ACTION_USB_DEVICE_DETACHED: \n" + device.toString(), Toast.LENGTH_LONG).show();

                if (device != null) {
                    if (device == deviceFound) {
                        releaseUsb();
                    } else {
                        Toast.makeText(CameraActivity.this, "device == deviceFound, no call releaseUsb()\n" + device.toString() + "\n" + deviceFound.toString(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(CameraActivity.this,
                            "device == null, no call releaseUsb()", Toast.LENGTH_LONG).show();
                }
            }
        }

    };

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//END

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        //location manager instance
        locationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        };

        getGPSLocation();

        startCapture = (Button) findViewById(R.id.button);
//        normalPicture = (Button) findViewById(R.id.button3);
//        rollingImage = (Button) findViewById(R.id.rolling);
//        dcImage = (Button) findViewById(R.id.dc);


        textureView = (TextureView) findViewById(R.id.textureView);
        assert textureView != null;

        //register the broadcast receiver
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        connectUsb();

        //rolling image OnClickListener
//        rollingImage.setOnClickListener(new View.OnClickListener() {
//            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//            @Override
//            public void onClick(View view) {
//                try {
//                    rollingImageCapture(100);
////                    Toast.makeText(CameraActivity.this, "Process Done.", Toast.LENGTH_LONG).show();
//                    //need to delay or else createCameraPreview() will crash the application
//                    TimeUnit.SECONDS.sleep(1);
//                    createCameraPreview();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });

        //dc image OnClickListener
//        dcImage.setOnClickListener(new View.OnClickListener() {
//            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//            @Override
//            public void onClick(View view) {
//                try {
//                    dcImageCapture();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        });

        //first light capture
        startCapture.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View view) {
                //send certain string to enable first light capture with lcd shutter
                if (deviceFound != null) {
//                    String out = "1";
//                    byte[] bytesOut = out.getBytes(); //convert String to byte[]
//                    int usbResult = usbDeviceConnection.bulkTransfer(endpointOut, bytesOut, bytesOut.length, 0);
                } else {
//                    Toast.makeText(CameraActivity.this, "Device not found", Toast.LENGTH_SHORT).show();
                }

                try {
//                    repeatingCaptureThread.start();
                    modifiedTakePicture(500);
//                    rollingImageCapture(10);
//                    repeatingCaptureThread.interrupt();
                    TimeUnit.MILLISECONDS.sleep(500);
                    createCameraPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        //normal picture
//        normalPicture.setOnClickListener(new View.OnClickListener() {
//            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//            @Override
//            public void onClick(View view) {
//                takePicture();
//            }
//        });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void rollingImageCapture(final int numPics) {
//        if(latitude == 0 && longitude == 0){
//            getGPSLocation();
//            Toast.makeText(CameraActivity.this, "Location not changed!", Toast.LENGTH_SHORT).show();
//            return;
//        }
        if(cameraDevice == null) return;
        CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            //set up dimentions of jpeg with current camera jpeg characteristics(?)
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null){
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            //set width and height in case jpegSizes does not contain a width and length
            int width = 640;
            int height = 480;
            if(jpegSizes != null && jpegSizes.length > 0){
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            imageReader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageReader.getSurface());

            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            long expTime = 1250000; //in nanoseconds
            int ISO = 1000;
            float aperture = 2.0f;
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF); //auto exposure time, iso, and frame duration is turned off
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF); //auto focus turned off
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTime); //exposure time 1.25ms; value is in nano
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISO); //set ISO 1000
            captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture); //aperture set to 2.0

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            //create new list of Strings
            fileNameList = new ArrayList<String>();
            //create new list of Files
            fileList = new ArrayList<File>();
            //set numPictures to 0
            numPictures = 0;
            //set numTextFiles to 0
            numTextFiles = 0;

            File folder = new File(Environment.getExternalStorageDirectory()+"/Rolling_Image_Test");
            folder.mkdir();
            File[] list = folder.listFiles();
            int numFiles = list.length;
            String fileName;

            for(int i = 0; i < numPics; i++){
                fileName = "rolling_" + (numFiles + 1 + i);
                fileNameList.add(fileName);
                file = new File(folder.getPath()+"/"+fileName+".jpg");
                fileList.add(file);
            }

//            Toast.makeText(CameraActivity.this, fileName, Toast.LENGTH_SHORT).show();
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //initialize image
                    Image image = null;
                    try{
                        saveGPSLocation(fileNameList.get(numTextFiles));
                        numTextFiles++;
                        image = imageReader.acquireNextImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e){
                        e.printStackTrace();
                    } finally{
                        if(image != null) image.close();
                    }
                }
                private void save(byte[] bytes) throws IOException{
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(fileList.get(numPictures));
                        outputStream.write(bytes);
                        numPictures++;
                    } finally {
                        if(outputStream != null) outputStream.close();
                    }
                }
            };

            imageReader.setOnImageAvailableListener(readerListener,backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview(); /////////////////////////
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        //create a list of capture requests
                        List<CaptureRequest> captureList = new ArrayList<CaptureRequest>();
                        captureRequestBuilder.addTarget(imageReader.getSurface());

                        for(int i = 0; i < numPics; i++){
                            captureList.add(captureRequestBuilder.build());
                            captureRequestBuilder.addTarget(imageReader.getSurface());
                        }

                        cameraCaptureSession.setRepeatingBurst(captureList, captureListener, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void dcImageCapture() {
        if(cameraDevice == null) return;
        CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null){
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            //set width and height in case jpegSizes does not contain a width and length
            int width = 640;
            int height = 480;
            if(jpegSizes != null && jpegSizes.length > 0){
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            imageReader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageReader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //EDIT VALUES HERE ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            long expTime = 16666667; //in nanoseconds
            int ISO = 1000;
            float aperture = 2.0f;
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


            //maybe surfaceview is making the app crash
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF); //auto exposure time, iso, and frame duration is turned off
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF); //auto focus turned off
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTime); //set exposure time
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISO); //set ISO
            captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture); //set aperture

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

            File folder = new File(Environment.getExternalStorageDirectory()+"/DC_Image");
            folder.mkdir();
            File[] list = folder.listFiles();
            int numFiles = list.length;
            String fileName = "dc_" + (numFiles+1);
//            Toast.makeText(CameraActivity.this, fileName, Toast.LENGTH_SHORT).show();
            file = new File(folder.getPath()+"/"+fileName+".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //initialize image
                    Image image = null;
                    try{
                        image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e){
                        e.printStackTrace();
                    } finally{
                        if(image != null) image.close();
                    }
                }
                private void save(byte[] bytes) throws IOException{
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if(outputStream != null) outputStream.close();
                    }
                }
            };

            imageReader.setOnImageAvailableListener(readerListener,backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        cameraCaptureSession.capture(captureRequestBuilder.build(),captureListener,backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void takePicture() {
        if(cameraDevice == null) return;
        CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
//        Toast.makeText(CameraActivity.this, "Capturing Picture...", Toast.LENGTH_SHORT).show();
        try{

//            String str1 = "\0"; //sending "NUL" char
//            String str2 = "\0"; //sending "NUL" char
//            final byte[] bytesOut1 = str1.getBytes(); //convert String to byte[]
//            final byte[] bytesOut2 = str2.getBytes(); //convert String to byte[]

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null){
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            //set width and height in case jpegSizes does not contain a width and length
            int width = 640;
            int height = 480;
            if(jpegSizes != null && jpegSizes.length > 0){
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            imageReader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageReader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            File folder = new File(Environment.getExternalStorageDirectory()+"/LightCapture");
            folder.mkdir();

            Date now = new Date();
            int year = now.getYear() + 1900;
            int month = now.getMonth() + 1;
            int day = now.getDate();
            int hours = now.getHours();
            int minutes = now.getMinutes();
            int seconds = now.getSeconds();

            String date = month + "-" + day + "-" + year + "_" + hours + ":" + minutes + ":" + seconds;

            file = new File(folder.getPath()+"/"+date+".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //initialize image
                    Image image = null;
                    try{
                        image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e){
                        e.printStackTrace();
                    } finally{
                        if(image != null) image.close();
                    }
                }

                private void save(byte[] bytes) throws IOException{
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if(outputStream != null) outputStream.close();
                    }
                }
            };

            imageReader.setOnImageAvailableListener(readerListener,backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        cameraCaptureSession.capture(captureRequestBuilder.build(),captureListener,backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void modifiedTakePicture(final int picture_limit) {
        if(cameraDevice == null) return;
        CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
//        Toast.makeText(CameraActivity.this, "Capturing Picture...", Toast.LENGTH_SHORT).show();

        try{
//            String str1 = "\0"; //sent in ascii
//            String str2 = "5"; //sent in ascii
//            final byte[] bytesOut1 = str1.getBytes(); //convert String to byte[]
//            final byte[] bytesOut2 = str2.getBytes(); //convert String to byte[]

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null){
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            //set width and height in case jpegSizes does not contain a width and length
            int width = 640;
            int height = 480;
            if(jpegSizes != null && jpegSizes.length > 0){
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            //need to set up a new surface in order to do a burst capture
            imageReader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageReader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            Range<Integer> ISORange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            int upperISO = ISORange.getUpper();

            Range<Long> ExposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            long lower = ExposureRange.getLower();
//            long upper = ExposureRange.getUpper();
//            min exposure time: 9432ns
//            long newLower = 25000000; //25 ms; seems to be the right exposure time                                                           ////////////////////////
            long newLower = 250000; //0.25ms; 0.00025ms actually??
//            long upper = ExposureRange.getUpper();
//            Toast.makeText(CameraActivity.this, newLower + "", Toast.LENGTH_LONG).show();

            float[] apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
            float numbers = apertures[0];
//            Toast.makeText(CameraActivity.this, "" + numbers, Toast.LENGTH_SHORT).show();

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);    ////////////////////////
//            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, upper);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, newLower); //set digital shutter exposure time to newLower
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, upperISO); //set to max ISO
            captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, 2.0f);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, (long) 1);
//            Toast.makeText(CameraActivity.this, "" + upperISO, Toast.LENGTH_SHORT).show();

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            File folder = new File(Environment.getExternalStorageDirectory()+"/Repeating_Burst_Test");
            File[] list = folder.listFiles();
            folder.mkdir();

            //make new fileList
            fileList = new ArrayList<File>(picture_limit);
            //set numPictures to 0
            numPictures = 0;
            //set numTextFiles to 0
            numTextFiles = 0;
            //create new fileNameList
            fileNameList = new ArrayList<String>(picture_limit);

            //number of files
            int numFiles = list.length;
            String fileName;

            for(int i = 0; i < picture_limit; i++){
                fileName = "burst_" + (numFiles + i + 1);
                fileNameList.add(fileName);
                file = new File(folder.getPath() + "/" + fileName + ".jpg");
                fileList.add(file);
            }

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                //this happens after the outputSurface is created I think
                public void onImageAvailable(ImageReader imageReader) {
                    //initialize image
                    Image image = null;
                    try{
//                        backgroundHandler.post(new Image)
                        saveGPSLocation(fileNameList.get(numTextFiles)); //need to figure out what to do with modifiedTakePicture
                        numTextFiles++;
                        Log.d("NumTestFiles Counter", Integer.toString(numTextFiles));
                        image = imageReader.acquireNextImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                        Log.d("OnImageAvailable", "saving");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e){
                        e.printStackTrace();
                    }finally{
                        if(image != null) image.close();
//                        Log.d("OnImageAvailable", "closing");
                    }
                }
                private void save(byte[] bytes) throws IOException{
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(fileList.get(numPictures));
                        outputStream.write(bytes);
                        numPictures++;
                        Log.d("numPictures Counter", Integer.toString(numPictures));
                    } finally {
                        if(outputStream != null) outputStream.close();
                    }
                }
            };

            imageReader.setOnImageAvailableListener(readerListener,backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
//                    Toast.makeText(CameraActivity.this,"Picture Path: "+file,Toast.LENGTH_SHORT).show();

                }
            };
            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        List<CaptureRequest> repeatingList = new ArrayList<CaptureRequest>();
                        captureRequestBuilder.addTarget(imageReader.getSurface());
                        repeatingList.add(captureRequestBuilder.build());
                        //repeat
                        for(int i = 0; i < picture_limit-1; i++){ //why do i need to do one less here??
                            repeatingList.add(captureRequestBuilder.build());
                            captureRequestBuilder.addTarget(imageReader.getSurface());
                        }
//                        captureRequestBuilder.addTarget(imageReader.getSurface());
                        cameraCaptureSession.setRepeatingBurst(repeatingList, null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
        try{
            cameraID = manager.getCameraIdList()[0]; //get back camera id
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID); //get characteristics of hardware capabilities
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },requestPermission);
                return;
            }
            manager.openCamera(cameraID, stateCallback, null);
        } catch(CameraAccessException e){
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraPreview() {
        try{
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null) return; //camera is closed
                    captureSession = cameraCaptureSession;
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
                    try{
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(CameraActivity.this, "Changed", Toast.LENGTH_SHORT);
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //requests permission to use camera from user
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == requestPermission){
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        if(requestCode == 10){
            if (ActivityCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.INTERNET}
                            ,10);
                }
                Toast.makeText(CameraActivity.this, "requesting permission...", Toast.LENGTH_LONG).show();
                return;
            }
            else{
                Toast.makeText(CameraActivity.this,"requesting location", Toast.LENGTH_SHORT).show();
                locationManager.requestLocationUpdates("gps", 0, 0, locationListener);
            }
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    //onPause and onResume methods
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onPause(){
        super.onPause();
        stopBackgroundThread();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try{
            backgroundThread.join();
            //set both handler and thread to null (stop threads)
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(textureView.isAvailable()){
            openCamera();
        }
        else{
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void startRepeatingCaptureThread(){
        repeatingCaptureThread = new Thread("Repeating Capture");
        repeatingCaptureThread.start();

    }
}