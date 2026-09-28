package
com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StudentDao {
    @Insert
    void insertStudent(StudentEntity student);
    @Update
    void updateStudent(StudentEntity student);
    @Delete
    void deleteStudent(StudentEntity student);
    @Query("SELECT * FROM students")
    List<StudentEntity> getAllStudents();
    @Query("SELECT * FROM students WHERE studentId = :studentId")
    StudentEntity getStudentById(String studentId);
}