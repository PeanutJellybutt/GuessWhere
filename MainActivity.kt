package com.egci428.u5781070.guesswhere

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.SeekBar
import com.google.firebase.database.*
import ir.mirrajabi.searchdialog.SimpleSearchDialogCompat
import ir.mirrajabi.searchdialog.core.SearchResultListener
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val gameRoomsRef = firebaseDatabase.getReference("GAME_ROOM")
    private val roomNamesRef = firebaseDatabase.getReference("ROOM_NAMES")
    private var roomNameRef: DatabaseReference? = null

    private var roomName: String? = null
    private var roomNamesList: ArrayList<String> = ArrayList()
    private var roomNameExist = false

    private var roomKey: String? = null
    private var roomKeysCount: Int = 0
    private var roomKeysList: ArrayList<String> = ArrayList()

    private var playerName: String? = null
    private var playerColor: Int? = null

    private var currLat: Double? = null
    private var currLng: Double? = null
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null

    private var sensorManager: SensorManager? = null
    private var lastUpdate: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roomNameText.addTextChangedListener(object: TextWatcher {
            //Upon changing input room name, recheck if match any room names stored in the retrieved list
            override fun afterTextChanged(p0: Editable?) {
                roomName = p0.toString()

                setRoomNameListener(false)
                if ( !roomName.isNullOrEmpty() ) {
                    for (name in roomNamesList) {
                        if (roomName == name) {
                            setRoomNameListener(true)
                            break
                        }
                    }
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
        })

        //Check if exist a room on Firebase with its name same as the inputted room name
        roomNamesRef.addChildEventListener(object : ChildEventListener {
            //Retrieve room names and store it in an ArrayList
            override fun onChildAdded(p0: DataSnapshot?, p1: String?) {
                val name = p0!!.ref.key.toString()
                roomNamesList.add(name)
                Log.d("ADD_NAMES_LIST",name)

                //If room with the inputted name exists, set a child listener on that ROOM_NAMES/"name"
                if ( (!roomNameExist) and (!roomName.isNullOrEmpty()) and ( roomName == name ) )
                    setRoomNameListener(true)
            }

            //Remove room name from the ArrayList if the room is no longer exists
            override fun onChildRemoved(p0: DataSnapshot?) {
                val name = p0!!.ref.key.toString()
                roomNamesList.remove(name)
                Log.d("REMOVE_NAMES_LIST",name)

                //If no longer exist rooms with the inputted room name, remove the child listener from that ROOM_NAMES/"name"
                if ( (roomNameExist) and (!roomName.isNullOrEmpty()) and ( roomName == name ) )
                    setRoomNameListener(false)
            }
            override fun onCancelled(p0: DatabaseError?) {}
            override fun onChildChanged(p0: DataSnapshot?, p1: String?) {}
            override fun onChildMoved(p0: DataSnapshot?, p1: String?) {}
        })

        //Getting current location
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listener = object: LocationListener {
            override fun onLocationChanged(p0: Location?) {
                currLat = p0!!.latitude as Double
                currLng = p0!!.longitude as Double
            }
            override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
            override fun onProviderEnabled(p0: String?) {}
            override fun onProviderDisabled(p0: String?) {}
        }
        requestLocationService()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lastUpdate = System.currentTimeMillis()

        //Set starting player's color
        randomizeColor()

        //Listen to player's color seekbar
        val seekBarListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                playerColor = Color.argb(255, redSlider.progress, greenSlider.progress, blueSlider.progress)
                playerIcon.setColorFilter(playerColor!!)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) { }
            override fun onStopTrackingTouch(p0: SeekBar?) { }
        }
        redSlider.setOnSeekBarChangeListener(seekBarListener)
        greenSlider.setOnSeekBarChangeListener(seekBarListener)
        blueSlider.setOnSeekBarChangeListener(seekBarListener)

        hostBtn.setOnClickListener {
            //If room name was not inputted, do nothing
            if ( roomName.isNullOrEmpty() or (currLat == null) or (currLng == null) ) {
                return@setOnClickListener
            }

            //If name not entered
            if ( nameText.text.isNullOrEmpty() )
                playerName = nameGenerate("HOST_NAME", false)
            else playerName = nameText.text.toString()

            //Get current location
            requestLocationService()

            //Start a lobby as the host
            var intent = Intent(applicationContext, LobbyActivity::class.java)
            intent.putExtra("name", playerName)
            intent.putExtra("color", playerColor!!)
            intent.putExtra("host", true)
            intent.putExtra("room", roomNameText.text.toString())
            intent.putExtra("lat", currLat)
            intent.putExtra("lng", currLng)
            startActivity(intent)
            currLat = null
            currLng = null
        }

        joinBtn.setOnClickListener {
            //If room with the inputted name doesn't exit, inform the user
            if ( !roomNameExist ) {
                roomNameText.text.clear()
                roomNameText.hint = "ROOM DOESN'T EXIST!"
                return@setOnClickListener
            }

            if (roomKeysCount == 1) {
                roomKey = roomKeysList[0]
                joinRoom()
            } else if (roomKeysCount > 1) {
                //In-case of joining duplicated name room, must select key in the browse window first
                SimpleSearchDialogCompat(this@MainActivity, "Select Room Key", "Enter the room key here", null, searchableKeyInit(),
                    SearchResultListener { baseSearchDialogCompat, item, position ->
                        roomKey = item.title
                        baseSearchDialogCompat.dismiss()
                        if ( roomKey != null )
                            joinRoom()
                    }
                ).show()
            }
        }

        findBtn.setOnClickListener {

            if ( (currLat == null) or (currLng == null) )
                return@setOnClickListener

            //Listen for any available game lobby for once this time only when findBtn is clicked
            gameRoomsRef.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(p0: DataSnapshot?) {
                    var minDist = distLatLng(0.0, 0.0, 90.0, 180.0)
                    //Find the closest lobby to join
                    for ( room in p0!!.children ) {
                        val closed = room!!.child("closed").value.toString().toInt()
                        val started = room!!.child("started").value.toString().toInt()
                        if ((closed == 0) and (started == 0)) {
                            val roomLat = room!!.child("roomLat").value.toString().toDouble()
                            val roomLng = room!!.child("roomLng").value.toString().toDouble()
                            val dist = distLatLng(currLat!!, currLng!!, roomLat, roomLng)
                            if ( dist < minDist ) {
                                minDist = dist
                                roomName = room!!.child("name").value.toString()
                                roomKey = room!!.key
                            }
                        }
                    }

                    if ( roomKey != null ) {

                        //If name not entered
                        if ( nameText.text.isNullOrEmpty() )
                            playerName = nameGenerate("MATCHMADE_", true)
                        else playerName = nameText.text.toString()

                        //Join the found lobby
                        var intent = Intent(applicationContext, LobbyActivity::class.java)
                        intent.putExtra("name", playerName)
                        intent.putExtra("color", playerColor!!)
                        intent.putExtra("host", false)
                        intent.putExtra("room", roomName)
                        intent.putExtra("key", roomKey)
                        startActivity(intent)
                        currLat = null
                        currLng = null
                    }
                }
                override fun onCancelled(p0: DatabaseError?) { }
            })
        }
    }

    //Function to set or remove a child listener from a ROOM_NAMES/"name"
    private fun setRoomNameListener (set: Boolean) {
        if ( set ) {
            roomNameExist = true
            roomNameRef = roomNamesRef.child(roomName).ref

            //Add a child listener for the room keys within ROOM_NAMES/"name"
            roomNameRef!!.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(p0: DataSnapshot?, p1: String?) {
                    roomKeysList.add(p0!!.ref.key.toString())
                    roomKeysCount++
                }
                override fun onChildRemoved(p0: DataSnapshot?) {
                    roomKeysList.remove(p0!!.ref.key.toString())
                    roomKeysCount--
                    Log.d("ROOM KEY REMOVE", roomKeysList.size.toString())
                }
                override fun onChildChanged(p0: DataSnapshot?, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot?, p1: String?) {}
                override fun onCancelled(p0: DatabaseError?) {}
            })
        } else {
            roomNameExist = false
            roomNameRef = null
            roomKeysList.clear()
            roomKeysCount = 0
        }
    }

    private fun searchableKeyInit(): ArrayList<KeySelectModel>?{
        val items = ArrayList<KeySelectModel>()
        for ( key in roomKeysList ) {
            items.add(KeySelectModel(key))
        }

        return items
    }

    private fun joinRoom() {

        //Listen the chosen lobby for once this time only (when the function is called)
        val gameRoomRef = gameRoomsRef.child("$roomKey")
        gameRoomRef.addListenerForSingleValueEvent(object: ValueEventListener {

            //Check whether the chosen lobby is joinable or not
            override fun onDataChange(p0: DataSnapshot?) {
                val closed = p0!!.child("closed").value.toString().toInt()
                val started = p0!!.child("started").value.toString().toInt()
                if ( ( closed == 0 ) and ( started == 0 ) ) {

                    //****** If passworded, must enter password before entering? *******

                    //If name not entered
                    if ( nameText.text.isNullOrEmpty() )
                        playerName = nameGenerate("JOINED_", true)
                    else playerName = nameText.text.toString()

                    //Join the chosen lobby
                    var intent = Intent(applicationContext, LobbyActivity::class.java)
                    intent.putExtra("name", playerName)
                    intent.putExtra("color", playerColor!!)
                    intent.putExtra("host", false)
                    intent.putExtra("room", roomName)
                    intent.putExtra("key", roomKey)
                    startActivity(intent)
                    currLat = null
                    currLng = null
                } else if ( started == 1 )
                    Log.d("ROOM", "GAME ALREADY STARTED")
                else if ( closed == 1 )
                    Log.d("ROOM", "THIS LOBBY IS NOT ACCEPTING NEW PLAYERS")
            }
            override fun onCancelled(p0: DatabaseError?) { }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            10 -> requestLocationService()
            else -> {}
        }
    }

    private fun requestLocationService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET), 10)
            }
        }

        locationManager!!.requestLocationUpdates("gps", 1000, 0f, listener, null)
    }

    private fun distLatLng (lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371.0 //km
        val dLat = Math.toRadians((lat2 - lat1))
        val dLng = Math.toRadians((lng2 - lng1))
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun nameGenerate (prefix: String, randNum: Boolean): String {
        var name = prefix
        if (randNum) {
            val rnd = Random()
            name += rnd.nextInt(256).toString()
        }
        return name
    }

    private fun randomizeColor() {
        val rnd = Random()
        redSlider.progress = rnd.nextInt(256)
        greenSlider.progress = rnd.nextInt(256)
        blueSlider.progress = rnd.nextInt(256)
        playerColor = Color.argb(255, redSlider.progress, greenSlider.progress, blueSlider.progress)
        playerIcon.setColorFilter(playerColor!!)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER)
            getAccelerometer(event)
    }
    private fun getAccelerometer(event: SensorEvent) {
        val values = event.values
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val accel = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
        val actualTime = System.currentTimeMillis()
        if (accel >= 2) {
            if (actualTime - lastUpdate < 200) {
                return
            }

            lastUpdate = actualTime
            randomizeColor()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
    override fun onResume() {
        super.onResume()
        sensorManager!!.registerListener(this, sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }
    override fun onPause() {
        super.onPause()
        sensorManager!!.unregisterListener(this)
    }

}
