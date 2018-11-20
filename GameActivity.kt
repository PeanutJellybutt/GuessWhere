package com.egci428.u5781070.guesswhere

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_game.*

class GameActivity : AppCompatActivity() {

    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var ROOM_NAME: String? = null
    private var ROOM_KEY: String? = null

    private var gameRef: DatabaseReference? = null
    private var flagPlayRef: DatabaseReference? = null
    private var flagQuizRef: DatabaseReference? = null
    private var flagResultsRef: DatabaseReference? = null
    private var ansRef: DatabaseReference? = null
    private var readyRef: DatabaseReference? = null

    private var quizAsk: String? = null
    private var quizAns: String? = null
    private var quizType: String? = null //fixed, dist, bonus
    private var quizLat: Double? = null
    private var quizLng: Double? = null
    private var quizTimer: Int? = null

    private var myKey: String? = null
    private var answerable = false
    private val scoreMap = hashMapOf<String,Int>()
    private var endGame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val bundle = intent.extras
        ROOM_NAME = bundle.get("name").toString()
        ROOM_KEY = bundle.get("key").toString()
        val isHost = bundle.get("host") as Boolean
        myKey = bundle.get("my_key").toString()
        var playersList = bundle.get("players") as ArrayList<Player>
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
                        gameRef!!.child("question/ans").setValue(quizAns)
                        gameRef!!.child("question/type").setValue(quizType)
                        gameRef!!.child("question/lat").setValue(quizLat)
                        gameRef!!.child("question/lng").setValue(quizLng)
                        gameRef!!.child("question/timer").setValue(quizTimer)
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
                    val score = calculateScore(playerAns)
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
                        for ( player in playersList ) {
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
        val quizRefListener = object: ValueEventListener {
            override fun onDataChange(p0: DataSnapshot?) {
                val flag = p0!!.value.toString().toInt()
                if (flag == 1 ) {
                    Log.d("PLAYER: FLAG CHECK","QUIZ 1")

                    //Retrieve question from Firebase
                    val questionRef = gameRef!!.child("question").ref
                    questionRef.addListenerForSingleValueEvent(object: ValueEventListener {
                        override fun onDataChange(p0: DataSnapshot?) {
                            quizAsk = p0!!.child("ask").value.toString()
                            quizAns = p0!!.child("ans").value.toString()
                            quizType = p0!!.child("type").value.toString()
                            quizLat = p0!!.child("lat").value.toString().toDouble()
                            quizLng = p0!!.child("lng").value.toString().toDouble()
                            quizTimer = p0!!.child("timer").value.toString().toInt()
                            Log.d("PLAYER: QUESTION","DOWNLOADED")

                            //*************** SUPPOSED TO BE PLAY THE GAME PART *************************
                            answerable = true
                            Log.d("PLAYER: GAME","THINKING/ANSWERING")
                        }
                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }
            }
            override fun onCancelled(p0: DatabaseError?) { }
        }
        flagQuizRef!!.addValueEventListener(quizRefListener)

        //Upload answer to Firebase
        ansBtn.setOnClickListener {
            answerable = false

            ansRef!!.child(myKey).setValue("THIS IS MY ANSWER!")
            Log.d("PLAYER: ANSWERS","PUSHED $myKey")
        }

        //Wait for host to finish calculating scores and upload it to Firebase, either for round end or match end
        val resultsRefListener = object: ValueEventListener {

            val thisListener = this

            override fun onDataChange(p0: DataSnapshot?) {
                val flag = p0!!.value.toString().toInt()
                if ( flag != 0 ) {
                    Log.d("PLAYER: FLAG CHECK","RESULTS 1")

                    val playersRef = gameRef!!.child("PLAYERS").ref
                    playersRef.addListenerForSingleValueEvent(object: ValueEventListener {
                        override fun onDataChange(p0: DataSnapshot?) {

                            //Retrieve scores for each players
                            for ( player in playersList ) {
                                val key = player.key
                                player.earn = p0!!.child("$key/earn").value.toString().toInt()
                                player.total = p0!!.child("$key/total").value.toString().toInt()
                                Log.d("PLAYER: SCORES","DOWNLOADED $key")
                            }

                            val winner = p0!!.child("winner").value.toString()
                            if ( winner == "0" ) {
                                //If match is not ending (winner not declared), display round scoreboard

                                //*************** DISPLAY ROUND SCOREBOARD ******************

                                Log.d("PLAYER: SCOREBOARD","ROUND DISPLAY")

                                //Set individual ready flag to inform host device that the player is ready to continue next round
                                readyRef!!.push().setValue(true)
                                Log.d("PLAYER: READY","PUSHED $myKey")
                            } else {
                                //If match is ending (winner declared), display end of match scoreboard
                                Log.d("PLAYER: SCOREBOARD","WINNER IS $winner")

                                //*************** DISPLAY END OF MATCH SCOREBOARD ******************

                                Log.d("PLAYER: SCOREBOARD","MATCH DISPLAY")

                                //Remove database references listener and set individual ready flag to inform host device that the player is exiting the match
                                flagQuizRef!!.removeEventListener(quizRefListener)
                                flagResultsRef!!.removeEventListener(thisListener)
                                readyRef!!.push().setValue(true)
                                Log.d("PLAYER: READY","PUSHED $myKey")
                                finish()
                            }
                        }
                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }
            }
            override fun onCancelled(p0: DatabaseError?) { }
        }
        flagResultsRef!!.addValueEventListener(resultsRefListener)
    }

    private fun setQuiz() {

        //**************** QUESTION SELECTION HERE ******************

        //TEST FOR NOW
        quizAsk = "What is the name of this city?"
        quizAns = "Bangkok"
        quizType = "fixed"
        quizLat = 5.0
        quizLng = 5.0
        quizTimer = 10000
    }

    private fun calculateScore(ans: String): Int {
        
        //**************** CALCULATE SCORE WITH THE PROVIDED ANSWER HERE ******************

        //TEST FOR NOW
        return 10
    }
}
