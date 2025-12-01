package com.example.locaris;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<MainActivity.ChatMessage> messages = new ArrayList<>();
    private String myUuid;
    private Context context;

    public ChatAdapter(String myUuid) {
        this.myUuid = myUuid;
    }

    public void setMessages(List<MainActivity.ChatMessage> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        // Usamos nuestro layout personalizado ahora
        View v = LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        MainActivity.ChatMessage msg = messages.get(position);

        // Configurar texto
        holder.textView.setText(msg.text);

        boolean isMe = msg.from_device != null && msg.from_device.equals(myUuid);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.textView.getLayoutParams();

        // Obtener el fondo (bubble) para cambiarle el color
        GradientDrawable background = (GradientDrawable) holder.textView.getBackground();

        if (isMe) {
            // MIS MENSAJES: Derecha, Fondo Negro, Texto Hueso
            params.gravity = Gravity.END;
            background.setColor(ContextCompat.getColor(context, R.color.bubble_me));
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_me));
        } else {
            // OTROS MENSAJES: Izquierda, Fondo Gris, Texto Negro
            params.gravity = Gravity.START;
            background.setColor(ContextCompat.getColor(context, R.color.bubble_other));
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_other));
        }

        holder.textView.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv_chat_msg);
        }
    }
}
