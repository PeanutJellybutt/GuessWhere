package com.egci428.u5781070.guesswhere

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_scoreboard.*
import java.util.ArrayList

class ScoreboardActivity : AppCompatActivity() {

    private var scoreboardAdapter: ScoreboardAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scoreboard)

        val bundle = intent.extras
        val time = bundle.get("time") as Long
        var playersList = bundle.get("players") as ArrayList<Player>
        ansView.text = bundle.get("answer").toString()

        //Prep scoreboard
        scoreboardAdapter = ScoreboardAdapter(this, R.layout.scoring_list, playersList)
        listView.adapter = scoreboardAdapter

        //Countdown before closing scoreboard
        object : CountDownTimer(time, 1000) {
            override fun onTick(millisUntilFinished: Long) { }
            override fun onFinish() {
                val resultIntent = Intent()
                setResult(Activity.RESULT_OK, resultIntent);
                finish()
            }
        }.start()
    }

    class ScoreboardAdapter (val mContext: Context, val layoutResId: Int, val playersList: List<Player>): ArrayAdapter<Player>(mContext, layoutResId, playersList) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

            val view: View
            if (convertView == null) {
                val layoutInflater: LayoutInflater = LayoutInflater.from(mContext)
                view = layoutInflater.inflate(layoutResId, null)
            } else {
                view = convertView
            }

            val player = playersList[position]

            val nameView = view!!.findViewById<TextView>(R.id.nameView)
            val iconView = view!!.findViewById<ImageView>(R.id.iconView)
            val earnView = view!!.findViewById<TextView>(R.id.earnView)
            val totalView = view!!.findViewById<TextView>(R.id.totalView)
            nameView.text = player.name
            //nameView.setTextColor(player.color)
            iconView.setColorFilter(player.color)
            val earn = "+ " + (player.earn).toString()
            val total = "= " + (player.total).toString()
            earnView.text = earn
            totalView.text = total
            if ( player.won ) {
                val winnerView = view!!.findViewById<ImageView>(R.id.winnerView)
                winnerView.visibility = View.VISIBLE
            }

            return view!!
        }
    }
}
