package com.egci428.u5781070.guesswhere

import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_lobby.*
import java.util.*
import android.widget.CompoundButton
import android.widget.SeekBar

class LobbyActivity : AppCompatActivity() {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var gameRoomRef: DatabaseReference? = null
    private var ROOM_NAME: String? = null
    private var ROOM_KEY: String? = null

    private var playersCount: Int = 0 //FB
    private var playersRef: DatabaseReference? = null
    private var playersListener: ValueEventListener? = null
    private var playersList: ArrayList<Player> = ArrayList<Player>()
    private var playersListAdapter: LobbyPlayerAdapter? = null

    private lateinit var currentPlayer: Player

    private var questionsCount = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)

        //Initializing Game Lobby (Hosting or Joining)
        val bundle = intent.extras
        val isHost = bundle.get("host") as Boolean

        ROOM_NAME = bundle.get("room").toString()
        if ( isHost ) {
            //Reserve new lobby and its key
            gameRoomRef = firebaseDatabase.getReference("GAME_ROOM").push()
            ROOM_KEY = gameRoomRef!!.key

            //Setting initial lobby values
            gameRoomRef!!.child("name").setValue(ROOM_NAME)
            gameRoomRef!!.child("closed").setValue(0)
            gameRoomRef!!.child("started").setValue(0)

            val lat = bundle.get("lat") as Double
            val lng = bundle.get("lng") as Double
            gameRoomRef!!.child("roomLat").setValue(lat)
            gameRoomRef!!.child("roomLng").setValue(lng)

            //Index room key to ROOM_NAMES
            firebaseDatabase.getReference("ROOM_NAMES/$ROOM_NAME/$ROOM_KEY").setValue(true)

            //Closing down lobby
            exitBtn.setOnClickListener {
                //Set start flag to -1 to acknowledge players' device that lobby is shutting down
                gameRoomRef!!.child("started").setValue(-1)

                //Remove own's player
                val myKey = currentPlayer.key
                playersRef!!.child(myKey).removeValue()
            }

        } else {
            //Get reference to the lobby with the given key
            ROOM_KEY = bundle.get("key").toString()
            gameRoomRef = firebaseDatabase.getReference("GAME_ROOM/$ROOM_KEY")
        }

        //Display lobby name and its key
        val lobbyColor = bundle.get("color").toString().toInt()
        roomNameView.text = ROOM_NAME
        roomKeyView.text = ROOM_KEY
        roomNameView.setTextColor(lobbyColor)

        //Joining a lobby either as the host or client
        playersRef = gameRoomRef!!.child("PLAYERS")
        currentPlayer = Player.createPlayer(playersRef!!.push(),bundle.get("name").toString(),bundle.get("color").toString().toInt())
        playersList.add(currentPlayer)
        playersCount++

        //If is not the host, listen for when the host starts the game or close the lobby
        if ( !isHost ) {
            gameRoomRef!!.child("started").addValueEventListener(object: ValueEventListener {
                override fun onDataChange (p0: DataSnapshot) {
                    if ( p0.value != null ) {
                        val flag = p0.value.toString().toInt()
                        if (flag == 1) {
                            Log.d("PLAYER: GAME ", "STARTED")

                            //Start the main game activity as a client player
                            val intent = Intent(applicationContext, GameActivity::class.java)
                            intent.putExtra("name", ROOM_NAME)
                            intent.putExtra("key", ROOM_KEY)
                            intent.putExtra("host", false)
                            intent.putExtra("my_key", currentPlayer.key)
                            intent.putExtra("players", playersList)
                            intent.putExtra("players_count", playersCount)
                            startActivity(intent)
                            finish()
                        } else if (flag == -1) {
                            //Lobby is shutting down, remove own's player
                            val myKey = currentPlayer.key
                            playersRef!!.child(myKey).removeValue()
                            finish()
                        }
                    }
                }
                override fun onCancelled(p0: DatabaseError?) { }
            })

            //Exit lobby, remove own's player
            exitBtn.setOnClickListener {
                val myKey = currentPlayer.key
                playersRef!!.child(myKey).removeValue()
                finish()
            }
        }

        //Setting up lobby players list view
        playersListAdapter = LobbyPlayerAdapter(this, R.layout.players_list, playersList)
        playersListView.adapter = playersListAdapter
        playersListener = object: ValueEventListener {
            //Re-update players list on players joined/exited
            override fun onDataChange (p0: DataSnapshot) {
                playersList.clear()
                playersCount = 0

                if (p0.hasChildren()) {
                    //If still has player in lobby
                    for (player in p0.children) {
                        if (!player.hasChild("name") or !player.hasChild("color"))
                            continue
                        val playerRef = player.ref
                        val playerName = player.child("name").value.toString()
                        val playerColor = player.child("color").value.toString().toInt()
                        val player = Player(playerRef.key, playerName, playerColor)
                        playersList.add(player)
                        playersCount++
                    }
                    playersListAdapter!!.notifyDataSetChanged()
                } else {
                    //End lobby if no player is left, remove room's key index within the ROOM_NAMES/"name"
                    firebaseDatabase.getReference("ROOM_NAMES/$ROOM_NAME/$ROOM_KEY").removeValue()
                    Log.d("HOST: ROOM","NAME INDEX REMOVED")

                    //Delete lobby from Firebase and remove listeners on database references
                    gameRoomRef!!.removeValue()
                    playersRef!!.removeEventListener(this)
                    Log.d("HOST: ROOM","LOBBY REMOVED")
                    Log.d("HOST: ROOM","EXITING")
                    finish()
                }
            }
            override fun onCancelled(p0: DatabaseError?) { }
        }
        playersRef!!.addValueEventListener(playersListener)

        if ( isHost ) {
            //START button only visible to the host, start the main game when pressed
            startBtn.visibility = View.VISIBLE
            startBtn.setOnClickListener {
                gameRoomRef!!.child("started").setValue(1)
                Log.d("HOST: GAME ", "STARTING")

                //Start the main game activity as a host player
                val intent = Intent(applicationContext, GameActivity::class.java)
                intent.putExtra("name", ROOM_NAME)
                intent.putExtra("key", ROOM_KEY)
                intent.putExtra("host", true)
                intent.putExtra("questions", questionsCount)
                intent.putExtra("my_key", currentPlayer.key)
                intent.putExtra("players", playersList)
                intent.putExtra("players_count", playersCount)
                startActivity(intent)
                finish()
            }

            //CLOSED check box only visible to the host, stop accepting more joining players when checked
            closedChk.visibility = View.VISIBLE
            closedChk.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener {
                buttonView, isChecked ->

                if ( isChecked )
                    gameRoomRef!!.child("closed").setValue(1)
                else gameRoomRef!!.child("closed").setValue(0)
            })

            lengthSlider.visibility = View.VISIBLE
            lengthSlider.setOnSeekBarChangeListener( object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    questionsCount = lengthSlider.progress + 1
                    lengthView.text = "Questions: $questionsCount"
                }
                override fun onStartTrackingTouch(p0: SeekBar?) { }
                override fun onStopTrackingTouch(p0: SeekBar?) { }
            })
        }
    }
}
