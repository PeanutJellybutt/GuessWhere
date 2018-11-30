package com.egci428.u5781070.guesswhere

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_game.*
import android.os.CountDownTimer
import java.util.*


class GameActivity : AppCompatActivity(), OnMapReadyCallback {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var ROOM_NAME: String? = null
    private var ROOM_KEY: String? = null

    private var isHost = false

    private var gameRef: DatabaseReference? = null
    private var flagPlayRef: DatabaseReference? = null
    private var flagQuizRef: DatabaseReference? = null
    private var flagResultsRef: DatabaseReference? = null
    private var ansRef: DatabaseReference? = null
    private var readyRef: DatabaseReference? = null

    private var quizRefListener: ValueEventListener? = null
    private var resultsRefListener: ValueEventListener? = null

    private val countriesList = Country.initializeCountries()
    private var quizAsk: String? = null
    private var quizAns: String? = null
    private var quizAnsInit: String? = null
    private var quizLat: Double? = null
    private var quizLng: Double? = null
    private var quizValue: Int = 1
    private var quizTimer: Long? = null
    private var quizAnswer: String? = null

    private var myKey: String? = null
    private var answerable = false
    private var ansTimer: CountDownTimer? = null
    private val scoreMap = hashMapOf<String,Int>()

    private val SCORE_ROUND_REQUEST = 1
    private val SCORE_MATCH_REQUEST = 2
    private var playersList: ArrayList<Player>? = null
    private var endGame = false

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val mapFragment = mapView as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        val bundle = intent.extras
        ROOM_NAME = bundle.get("name").toString()
        ROOM_KEY = bundle.get("key").toString()
        isHost = bundle.get("host") as Boolean
        myKey = bundle.get("my_key").toString()
        playersList = bundle.get("players") as ArrayList<Player>
        var playersCount = bundle.get("players_count").toString().toInt()

        //Set database references that will be used
        gameRef = firebaseDatabase.getReference("GAME_ROOM/$ROOM_KEY")
        flagPlayRef = gameRef!!.child("play").ref
        flagQuizRef = gameRef!!.child("quiz").ref
        flagResultsRef = gameRef!!.child("results").ref
        ansRef = gameRef!!.child("answers").ref
        readyRef = gameRef!!.child("ready").ref

        if ( isHost )    {

            //Initialize flags that will be used
            Log.d("HOST: ROOM","IS HOST")
            var questionLeft = bundle.get("questions").toString().toInt()
            gameRef!!.child("PLAYERS/winner").setValue("0")
            flagPlayRef!!.setValue(1)
            flagQuizRef!!.setValue(0)
            flagResultsRef!!.setValue(0)
            Log.d("HOST: ROOM","INITIALIZED FLAGS")

            //Start new question
            val playRefListener = object : ValueEventListener {
                override fun onDataChange(p0: DataSnapshot?) {
                    val flag = p0!!.value.toString().toInt()
                    if ( flag == 1 ) {
                        Log.d("HOST: FLAG CHECK","PLAY 1")
                        p0!!.ref.setValue(0)

                        //Question select through the use of function setQuiz
                        setQuiz()
                        Log.d("HOST: QUESTION","CREATED")

                        //Upload the question to Firebase
                        gameRef!!.child("question/ask").setValue(quizAsk)
                        gameRef!!.child("question/lat").setValue(quizLat)
                        gameRef!!.child("question/lng").setValue(quizLng)
                        gameRef!!.child("question/timer").setValue(quizTimer)
                        gameRef!!.child("question/answer").setValue(quizAnswer)
                        Log.d("HOST: QUESTION","UPLOADED")

                        //Set quiz flag to acknowledge players' device that the question is ready
                        flagQuizRef!!.setValue(1)
                        Log.d("HOST: FLAG SET","QUIZ 1")
                    }
                }
                override fun onCancelled(p0: DatabaseError?) {}
            }
            flagPlayRef!!.addValueEventListener(playRefListener)

            //Check for and process answers
            val ansRefListener = object : ChildEventListener {

                private var ansCount = 0

                override fun onChildAdded(p0: DataSnapshot?, p1: String?) {
                    //Count every uploaded answers
                    ansCount++
                    Log.d("HOST: ANSWERS","RETRIEVED $ansCount")

                    //Retrieve, calculate and store each players' answers in a hashMap with player's key as the key
                    val playerKey = p0!!.key.toString()
                    val playerAns = p0!!.value.toString()
                    val score = calculateScore(playerAns) * quizValue
                    scoreMap[playerKey] = score
                    Log.d("HOST: SCORES","$score SCORED BY $playerKey")

                    //If all players' answers have been processed
                    if ( ansCount == playersCount ) {
                        Log.d("HOST: ANSWERS","ALL RETRIEVED")
                        ansCount = 0

                        //Reset flag for quiz, as the question has ended
                        flagQuizRef!!.setValue(0)
                        Log.d("HOST: FLAG SET","QUIZ 0")
                        //Clear all answers on the Firebase
                        ansRef!!.removeValue()
                        Log.d("HOST: ANSWERS","CLEARED")

                        //Accumulate players' score results and upload it to Firebase
                        var max = 0
                        var winner: String? = null
                        for ( player in playersList!! ) {
                            val key = player.key
                            Log.d("HOST: DEBUG","KEY $key")
                            val earn = scoreMap[key]!!
                            val total = player.total + earn
                            if ( total > max ) {
                                winner = key
                            }
                            Log.d("HOST: SCORES","ACCUMULATED FOR $key")

                            gameRef!!.child("PLAYERS/$key/earn").setValue(earn)
                            gameRef!!.child("PLAYERS/$key/total").setValue(total)
                            Log.d("HOST: SCORES","UPLOADED FOR $key")
                        }

                        //Set result flag to acknowledge players' device that the results are ready and uploaded
                        flagResultsRef!!.setValue(1)
                        Log.d("HOST: FLAG SET","RESULTS 1")

                        //If reach the end of the match, also upload the winning player's key to Firebase
                        questionLeft--
                        if ( questionLeft == 0 ) {
                            gameRef!!.child("PLAYERS/winner").setValue(winner)
                            gameRef!!.child("PLAYERS/$winner/won").setValue(true)
                            Log.d("HOST: RESULTS","WINNER IS $winner")
                            endGame = true
                        }
                    }
                }
                override fun onCancelled(p0: DatabaseError?) {}
                override fun onChildChanged(p0: DataSnapshot?, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot?, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot?) {}
            }
            ansRef!!.addChildEventListener(ansRefListener)

            //Check if players are ready for next question or to end the game
            val readyRefListener = object : ChildEventListener {

                private var readyCount = 0

                override fun onChildAdded(p0: DataSnapshot?, p1: String?) {
                    //Count readied players
                    readyCount++
                    Log.d("HOST: READY","$readyCount")

                    //If every players are ready
                    if ( readyCount == playersCount ) {
                        Log.d("HOST: READY","ALL IS READY")
                        readyCount = 0
                        readyRef!!.removeValue()
                        Log.d("HOST: READY","CLEARED")

                        if ( !endGame ) {
                            //If match is not ending, reset result flag and set play flag to continue to the next question
                            flagResultsRef!!.setValue(0)
                            Log.d("HOST: FLAG SET","RESULTS 0")
                            Log.d("HOST: ROOM","$questionLeft QUESTIONS LEFT")
                            flagPlayRef!!.setValue(1)
                            Log.d("HOST: FLAG SET","PLAY 1")
                        } else {
                            //On match end, remove room's key index within the ROOM_NAMES/"name"
                            firebaseDatabase.getReference("ROOM_NAMES/$ROOM_NAME/$ROOM_KEY").removeValue()
                            Log.d("HOST: ROOM","NAME INDEX REMOVED")

                            //Delete game room from Firebase and remove listeners on database references
                            gameRef!!.removeValue()
                            flagPlayRef!!.removeEventListener(playRefListener)
                            ansRef!!.removeEventListener(ansRefListener)
                            readyRef!!.removeEventListener(this)
                            Log.d("HOST: ROOM","GAME ROOM REMOVED")
                            Log.d("HOST: ROOM","ENDING")
                            finish()
                        }
                    }
                }
                override fun onCancelled(p0: DatabaseError?) {}
                override fun onChildChanged(p0: DataSnapshot?, p1: String?) {}
                override fun onChildMoved(p0: DataSnapshot?, p1: String?) {}
                override fun onChildRemoved(p0: DataSnapshot?) {}
            }
            readyRef!!.addChildEventListener(readyRefListener)
        }

        //Wait for host to finish uploading new question to Firebase, then retrieve the question and its info
        quizRefListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot?) {
                val flag = p0!!.value.toString().toInt()
                if (flag == 1 ) {
                    Log.d("PLAYER: FLAG CHECK","QUIZ 1")

                    //Retrieve question from Firebase
                    val questionRef = gameRef!!.child("question").ref
                    questionRef.addListenerForSingleValueEvent(object: ValueEventListener {
                        override fun onDataChange(p0: DataSnapshot?) {
                            quizAsk = p0!!.child("ask").value.toString()
                            quizLat = p0!!.child("lat").value.toString().toDouble()
                            quizLng = p0!!.child("lng").value.toString().toDouble()
                            quizTimer = p0!!.child("timer").value as Long
                            quizAnswer = p0!!.child("answer").value.toString()
                            Log.d("PLAYER: QUESTION","DOWNLOADED")

                            quizGetReady()

                            Log.d("PLAYER: GAME","THINKING/ANSWERING")
                        }
                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }
            }
            override fun onCancelled(p0: DatabaseError?) { }
        }
        flagQuizRef!!.addValueEventListener(quizRefListener)

        //Wait for host to finish calculating scores and upload it to Firebase, either for round end or match end
        resultsRefListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot?) {
                val flag = p0!!.value.toString().toInt()
                if ( flag != 0 ) {
                    Log.d("PLAYER: FLAG CHECK","RESULTS 1")

                    val playersRef = gameRef!!.child("PLAYERS").ref
                    playersRef.addListenerForSingleValueEvent(object: ValueEventListener {
                        override fun onDataChange(p0: DataSnapshot?) {

                            //Retrieve scores for each players
                            for ( player in playersList!! ) {
                                val key = player.key
                                player.earn = p0!!.child("$key/earn").value.toString().toInt()
                                player.total = p0!!.child("$key/total").value.toString().toInt()
                                player.won = p0!!.child("$key/won").value as Boolean
                                Log.d("PLAYER: SCORES","DOWNLOADED $key")
                            }

                            val winner = p0!!.child("winner").value.toString()
                            if ( winner == "0" ) {
                                //If match is not ending (winner not declared), display round scoreboard
                                displayScoreboard(SCORE_ROUND_REQUEST,6000)
                                Log.d("PLAYER: SCOREBOARD","ROUND DISPLAY")
                            } else {
                                //If match is ending (winner declared), display end of match scoreboard
                                Log.d("PLAYER: SCOREBOARD","WINNER IS $winner")
                                displayScoreboard(SCORE_MATCH_REQUEST,10000)
                                Log.d("PLAYER: SCOREBOARD","MATCH DISPLAY")
                            }
                        }
                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }
            }
            override fun onCancelled(p0: DatabaseError?) { }
        }
        flagResultsRef!!.addValueEventListener(resultsRefListener)

        //Submit answer
        ansBtn.setOnClickListener {
            if ( (answerable) and (!ansInput.text.isNullOrEmpty()) ) {
                submitAnswer(ansInput.text.toString())
                ansInput.text.clear()
                ansTimer!!.cancel()
            }
        }
    }

    //Question setup/selection
    private fun setQuiz() {

        val rand = Random()
        val pos = rand.nextInt(countriesList.size)
        val country = countriesList[pos]

        quizAsk = "What is the name of this country?"
        quizAns = country.name
        quizAnsInit = country.initial
        quizLat = country.lat
        quizLng = country.lng
        quizValue = rand.nextInt(1) + 1
        quizTimer = 20000

        quizAnswer = "$quizAnsInit : $quizAns"

        countriesList.removeAt(pos)
    }

    //Pre question
    private fun quizGetReady() {
        mMap.clear()
        val pos = LatLng(0.0, 0.0)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, -4f))
        Toast.makeText(this@GameActivity, "GET READY..", Toast.LENGTH_LONG).show()

        timerView.text = "Processing..."
        timerView.setTextColor(Color.BLACK)

        //start countdown
        object : CountDownTimer(3500, 1000) {
            override fun onTick(millisUntilFinished: Long) { }
            override fun onFinish() {
                quizStart()
            }
        }.start()
    }

    //Show question, marker and camera shift
    private fun quizStart() {
        quizView.text = quizAsk
        val pos = LatLng(quizLat!!, quizLng!!)
        mMap.addMarker(MarkerOptions().position(pos).title("Location Marker"))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 4f))

        answerable = true
        ansInput.isEnabled = true

        //start countdown
        ansTimer = object : CountDownTimer(quizTimer!!, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished > 0) {
                    timerView.text = "Seconds Remaining: " + (millisUntilFinished / 1000).toString()

                    if ( millisUntilFinished <= 7000 )
                        timerView.setTextColor(Color.RED)
                }
            }
            override fun onFinish() {
                if ( answerable ) {
                    if ( !ansInput.text.isNullOrEmpty() ) {
                        submitAnswer(ansInput.text.toString())
                        ansInput.text.clear()
                    } else {
                        submitAnswer("")
                    }
                }

                timerView.text = "Seconds Remaining: 0"
            }
        }.start()
    }

    //Upload answer to Firebase
    private fun submitAnswer(answer: String) {
        answerable = false
        ansInput.isEnabled = false

        ansRef!!.child(myKey).setValue(answer)
        Log.d("PLAYER: ANSWERS","PUSHED $myKey")
    }

    //Calculate score with the provided answer
    private fun calculateScore(ans: String): Int {
        if ( ans == quizAns )
            return 2
        else if  ( ans == quizAnsInit)
            return 1
        else return 0
    }

    private fun displayScoreboard(requestCode: Int, time: Long) {
        val intent = Intent(this, ScoreboardActivity::class.java)
        intent.putExtra("time",time)
        intent.putExtra("players",playersList)
        intent.putExtra("answer",quizAnswer)
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        // Check which request we're responding to
        if (requestCode == SCORE_ROUND_REQUEST) {
            // Make sure the request was successful
            if (resultCode == Activity.RESULT_OK && data != null) {
                //Set individual ready flag to inform host device that the player is ready to continue next round
                readyRef!!.push().setValue(true)
                Log.d("PLAYER: READY","PUSHED $myKey")
            }
        } else if (requestCode == SCORE_MATCH_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                //Remove database references listener and set individual ready flag to inform host device that the player is exiting the match
                flagQuizRef!!.removeEventListener(quizRefListener)
                flagResultsRef!!.removeEventListener(resultsRefListener)
                readyRef!!.push().setValue(true)
                Log.d("PLAYER: READY","PUSHED $myKey")
                if ( !isHost )
                    finish()
            }
        }
    }
}
