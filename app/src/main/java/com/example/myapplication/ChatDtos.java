package com.example.myapplication;

import java.util.List;

// JSON shapes used by the chat screens. Field names match the server (chat.js) exactly.
public class ChatDtos {

    public static class Room {
        public String room;       // e.g. "uni", "prog:3", "grp:2", "staff:uni"
        public String title;      // e.g. "Whole University"
        public String audience;   // e.g. "Lecturers only"
    }

    public static class RoomsResponse {
        public List<Room> rooms;
    }

    public static class Contact {
        public int account_id;
        public String role;           // "student" or "lecturer"
        public String name;
        public String program_name;   // null for lecturers
        public String group_name;     // null for lecturers
        public int has_key;           // 1 = has opened Chat, so private messages can be encrypted for them
    }

    public static class ContactsResponse {
        public List<Contact> contacts;
    }

    public static class Message {
        public long message_id;
        public int sender_id;
        public String sender_name;
        public String sender_role;
        public String body;           // room: the text; private: encrypted JSON (EncryptedPayload)
        public String created_at;
    }

    public static class MessagesResponse {
        public List<Message> messages;
    }

    public static class SendText {
        public String text;
        public SendText(String text) { this.text = text; }
    }

    // A private message after encryption on the phone. All values are Base64.
    public static class EncryptedPayload {
        public String iv;      // random number used once for AES-GCM
        public String ct;      // the encrypted text
        public String k_to;    // the message key, locked with the RECIPIENT's public key
        public String k_from;  // the same message key, locked with the SENDER's public key (so you can re-read your own)
    }

    public static class SendEncrypted {
        public EncryptedPayload payload;
        public SendEncrypted(EncryptedPayload payload) { this.payload = payload; }
    }

    public static class PublicKeyBody {
        public String public_key;
        public PublicKeyBody(String publicKey) { this.public_key = publicKey; }
    }

    public static class KeyResponse {
        public int account_id;
        public String public_key;
    }
}