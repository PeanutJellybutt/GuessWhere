package com.egci428.u5781070.guesswhere

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.database.DatabaseReference
import java.io.Serializable

class Player(key: String, name: String, color: Int): Serializable {
    var key: String = key
    var name: String = name
    var color: Int = color
    var earn: Int = 0
    var total: Int = 0
    var won = false

    companion object {
        fun createPlayer(ref: DatabaseReference, name: String, color: Int): Player {
            ref.child("name").setValue(name)
            ref.child("color").setValue(color.toString())
            ref.child("earn").setValue(0)
            ref.child("total").setValue(0)
            ref.child("won").setValue(false)

            return Player(ref.key,name,color)
        }
    }
}

class LobbyPlayerAdapter (val mContext: Context, val layoutResId: Int, val playersList: List<Player>): ArrayAdapter<Player>(mContext, layoutResId, playersList) {
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
        nameView.text = player.name
        //nameView.setTextColor(player.color)
        iconView.setColorFilter(player.color)

        return view!!
    }
}
