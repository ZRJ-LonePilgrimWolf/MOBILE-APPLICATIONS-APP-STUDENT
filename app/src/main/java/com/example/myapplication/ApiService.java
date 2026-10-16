package com.example.myapplication;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/login")
    Call<LoginResponse> login(@Body LoginRequest loginRequest);

    @GET("api/students")
    Call<StudentsResponse> getStudents(@Header("Authorization") String bearer);

    @POST("api/students")
    Call<ResponseBody> createStudent(@Header("Authorization") String bearer, @Body NewStudent body);


    // ---- Registration (registerSafe.js on the server) ----
    @POST("api/register/student")
    Call<ResponseBody> registerStudent(@Body RegisterStudentRequest body);

    @POST("api/register/lecturer")
    Call<ResponseBody> registerLecturer(@Body RegisterLecturerRequest body);

    // ---- Programmes + lab groups for the registration screen (reference.js) ----
    @GET("api/reference")
    Call<ReferenceResponse> getReference();


    // ---- Chat (chat.js on the server) ----
    @GET("api/chat/rooms")
    Call<ChatDtos.RoomsResponse> chatRooms(@Header("Authorization") String bearer);

    @GET("api/chat/rooms/{room}/messages")
    Call<ChatDtos.MessagesResponse> roomMessages(@Header("Authorization") String bearer,
                                                 @Path("room") String room,
                                                 @Query("after") long after);

    @POST("api/chat/rooms/{room}/messages")
    Call<ResponseBody> sendRoomMessage(@Header("Authorization") String bearer,
                                       @Path("room") String room,
                                       @Body ChatDtos.SendText body);

    @GET("api/chat/contacts")
    Call<ChatDtos.ContactsResponse> chatContacts(@Header("Authorization") String bearer);

    @PUT("api/chat/keys")
    Call<ResponseBody> publishKey(@Header("Authorization") String bearer,
                                  @Body ChatDtos.PublicKeyBody body);

    @GET("api/chat/keys/{id}")
    Call<ChatDtos.KeyResponse> getKey(@Header("Authorization") String bearer,
                                      @Path("id") int accountId);

    @GET("api/chat/dm/{id}/messages")
    Call<ChatDtos.MessagesResponse> dmMessages(@Header("Authorization") String bearer,
                                               @Path("id") int otherAccountId,
                                               @Query("after") long after);

    @POST("api/chat/dm/{id}/messages")
    Call<ResponseBody> sendDm(@Header("Authorization") String bearer,
                              @Path("id") int otherAccountId,
                              @Body ChatDtos.SendEncrypted body);

    @GET("api/chat/unread")
    Call<ChatDtos.UnreadResponse> chatUnread(@Header("Authorization") String bearer);

    @POST("api/chat/read")
    Call<ResponseBody> markRead(@Header("Authorization") String bearer,
                                @Body ChatDtos.MarkRead body);
}
