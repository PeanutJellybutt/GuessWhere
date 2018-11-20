package com.egci428.u5781070.guesswhere

import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_lobby.*
import java.util.*
import android.widget.CompoundButton



class LobbyActivity : AppCompatActivity() {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var gameRoomRef: DatabaseReference? = null
    private var ROOM_NAME: String? = null
    private var ROOM_KEY: String? = null

    private var playersCount: Int = 0 //FB
    private var playersRef: DatabaseReference? = null
    private var playersList: ArrayList<Player> = ArrayList<Player>()
    private var playersListAdapter: LobbyPlayerAdapter? = null

    private lateinit var currentPlayer: Player

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
            gameRoomRef!!.child("closed").setValue(0)
            gameRoomRef!!.child("password").setValue(0)
            gameRoomRef!!.child("roomLat").setValue(0)  //Should be set with location finding
            gameRoomRef!!.child("roomLng").setValue(0)  //Should be set with location finding
            gameRoomRef!!.child("started").setValue(0)

            //Index room key to ROOM_NAMES
            firebaseDatabase.getReference("ROOM_NAMES/$ROOM_NAME/$ROOM_KEY").setValue(true)
        } else {
            //Get reference to the lobby with the given key
            ROOM_KEY = bundle.get("key").toString()
            gameRoomRef = firebaseDatabase.getReference("GAME_ROOM/$ROOM_KEY")
        }

        //Display lobby name and its key
        roomNameView.text = ROOM_NAME
        roomKeyView.text = ROOM_KEY

        //Joining a lobby either as the host or client
        playersRef = gameRoomRef!!.child("PLAYERS")
        currentPlayer = Player.createPlayer(playersRef!!.push(),bundle.get("name").toString(),bundle.get("color").toString().toInt())
        playersList.add(currentPlayer)
        playersCount++

        //If is not the host, listen for when the host starts the game
        if ( !isHost ) {
            gameRoomRef!!.child("started").addValueEventListener(object: ValueEventListener {
                override fun onDataChange (p0: DataSnapshot) {
                    val flag = p0.value.toString().toInt()
                    if ( flag == 1 ) {
                        Log.d("PLAYER: GAME ","STARTED")

                        //Start the main game activity as a client player
                        val intent = Intent(applicationContext, GameActivity::class.java)
                        intent.putExtra("name",ROOM_NAME)
                        intent.putExtra("key",ROOM_KEY)
                        intent.putExtra("host",false)
                        intent.putExtra("my_key",currentPlayer.key)
                        intent.putExtra("players",playersList)
                        intent.putExtra("players_count",playersCount)
                        startActivity(intent)
                        finish()
                    }
                }
                override fun onCancelled(p0: DatabaseError?) { }
            })
        }

        //Setting up lobby players list view
        playersListAdapter = LobbyPlayerAdapter(this, R.layout.players_list, playersList)
        playersListView.adapter = playersListAdapter
        playersRef!!.addValueEventListener( object: ValueEventListener {
            //Re-update players list on players joined/exited
            override fun onDataChange (p0: DataSnapshot) {
                playersList.clear()
                playersCount = 0
                for (player in p0.children) {
                    if ( !player.hasChild("name") or !player.hasChild("color") )
                        continue
                    val playerRef = player.ref
                    val playerName = player.child("name").value.toString()
                    val playerColor = player.child("color").value.toString().toInt()
                    val player = Player(playerRef.key,playerName,playerColor)
                    playersList.add(player)
                    playersCount++
                }
                playersListAdapter!!.notifyDataSetChanged()
            }
            override fun onCancelled(p0: DatabaseError?) { }
        })

        if ( isHost ) {
            //FOR TESTING ONLY
            addBtn.visibility = View.VISIBLE
            addBtn.setOnClickListener {
                if ( playersCount < 8 ) {

                    val rnd = Random()
                    val playerName = "ADDED_" + rnd.nextInt(256).toString()
                    val playerColor = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
                    Player.createPlayer(playersRef!!.push(), playerName, playerColor)

                    playersCount++
                }
            }

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
                intent.putExtra("questions", 4)
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

            //PASSWORD input box only visible to the host, set lobby password
            passwordInput.visibility = View.VISIBLE
            passwordInput.addTextChangedListener(object: TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    val password = p0.toString()
                    if ( !password.isNullOrEmpty() )
                        gameRoomRef!!.child("password").setValue(password)
                    else gameRoomRef!!.child("password").setValue(0)
                }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            })
        }
    }
}
