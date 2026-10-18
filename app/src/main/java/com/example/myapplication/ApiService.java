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

    // ---- Student profile + lecturer approvals (approvals.js) ----
    @GET("api/student/me")
    Call<StudentDtos.Me> studentMe(@Header("Authorization") String bearer);

    @GET("api/approvals")
    Call<StudentDtos.ApprovalsResponse> approvals(@Header("Authorization") String bearer);

    @POST("api/approvals/{id}/approve")
    Call<ResponseBody> approveStudent(@Header("Authorization") String bearer,
                                      @Path("id") int studentId,
                                      @Body StudentDtos.ApproveBody body);

    @POST("api/approvals/{id}/reject")
    Call<ResponseBody> rejectStudent(@Header("Authorization") String bearer,
                                     @Path("id") int studentId);
    // ---- Student self-service + lecturer request handling (selfService.js) ----
    @PUT("api/student/me")
    Call<StudentDtos.UpdateMeResponse> updateMe(@Header("Authorization") String bearer,
                                                @Body StudentDtos.UpdateMe body);

    @GET("api/student/me/requests")
    Call<StudentDtos.MyRequestsResponse> myRequests(@Header("Authorization") String bearer);

    @POST("api/student/me/requests")
    Call<ResponseBody> newRequest(@Header("Authorization") String bearer,
                                  @Body StudentDtos.NewRequest body);

    @POST("api/student/me/requests/{id}/cancel")
    Call<ResponseBody> cancelRequest(@Header("Authorization") String bearer,
                                     @Path("id") int requestId);

    @GET("api/requests")
    Call<StudentDtos.LecturerRequestsResponse> lecturerRequests(@Header("Authorization") String bearer,
                                                                @Query("status") String status);

    @POST("api/requests/{id}/approve")
    Call<ResponseBody> approveRequest(@Header("Authorization") String bearer,
                                      @Path("id") int requestId);

    @POST("api/requests/{id}/reject")
    Call<ResponseBody> rejectRequest(@Header("Authorization") String bearer,
                                     @Path("id") int requestId,
                                     @Body StudentDtos.RejectBody body);
}