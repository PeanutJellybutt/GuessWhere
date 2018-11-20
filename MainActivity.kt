package com.egci428.u5781070.guesswhere

import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

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

        hostBtn.setOnClickListener {
            //If room name was not inputted, do nothing
            if ( roomName.isNullOrEmpty() ) {
                return@setOnClickListener
            }

            //Temporary name and color for host player
            playerName = "HOST_NAME"
            playerColor = Color.CYAN

            //Start a lobby as the host
            var intent = Intent(applicationContext, LobbyActivity::class.java)
            intent.putExtra("name", playerName)
            intent.putExtra("color", playerColor!!)
            intent.putExtra("host", true)
            intent.putExtra("room", roomNameText.text.toString())
            startActivity(intent)
        }

        joinBtn.setOnClickListener {
            //If room with the inputted name doesn't exit, inform the user
            if ( !roomNameExist ) {
                roomNameText.text.clear()
                roomNameText.hint = "ROOM DOESN'T EXIST!"
                return@setOnClickListener
            }

            //********* WIP *************
            //In-case of joining duplicated name room, must select key in the browse window first
            if ( roomKeysCount == 1 ) {
                roomKey = roomKeysList[0]
            } else if ( roomKeysCount > 1 ) {
                //browse key
                roomKey = roomKeysList[1] //FOR NOW
            }

            //Listen the chosen lobby for once only when the join button is pressed
            val gameRoomRef = gameRoomsRef.child("$roomKey")
            gameRoomRef.addListenerForSingleValueEvent(object: ValueEventListener {

                //Check whether the chosen lobby is joinable or not
                override fun onDataChange(p0: DataSnapshot?) {
                    val closed = p0!!.child("closed").value.toString().toInt()
                    val started = p0!!.child("started").value.toString().toInt()
                    if ( ( closed == 0 ) and ( started == 0 ) ) {

                        //***** If passworded, must enter password before entering? *******

                        //Temporary name and color for joined players
                        val rnd = Random()
                        playerName = "JOINED_" + rnd.nextInt(256).toString()
                        playerColor = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))

                        //Join the chosen lobby
                        var intent = Intent(applicationContext, LobbyActivity::class.java)
                        intent.putExtra("name", playerName)
                        intent.putExtra("color", playerColor!!)
                        intent.putExtra("host", false)
                        intent.putExtra("room", roomName)
                        intent.putExtra("key", roomKey)
                        startActivity(intent)
                    } else if ( started == 1 )
                        Log.d("ROOM", "GAME ALREADY STARTED")
                    else if ( closed == 1 )
                        Log.d("ROOM", "THIS LOBBY IS NOT ACCEPTING NEW PLAYERS")
                }
                override fun onCancelled(p0: DatabaseError?) { }
            })
        }

        browseBtn.setOnClickListener {
            //******* WIP ***********
            //Browse Available Game Lobby
            /*var intent = Intent(applicationContext, LobbyActivity::class.java)
            val rnd = Random()
            val playerName = "BROWSED_" + rnd.nextInt(256).toString()
            val playerColor = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            intent.putExtra("name", playerName)
            intent.putExtra("color", playerColor)
            intent.putExtra("host", false)
            startActivity(intent)*/
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
}
