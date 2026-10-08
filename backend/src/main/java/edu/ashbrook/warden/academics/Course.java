package edu.ashbrook.warden.academics;

import edu.ashbrook.warden.user.Department;
import edu.ashbrook.warden.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    private String code;

    private String title;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_code")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private User faculty;

    private short credits;

    private String semester;

    private int classesHeld;

    protected Course() {
    }

    public Course(String code, String title, Department department, User faculty, int credits, String semester,
                  int classesHeld) {
        this.code = code;
        this.title = title;
        this.department = department;
        this.faculty = faculty;
        this.credits = (short) credits;
        this.semester = semester;
        this.classesHeld = classesHeld;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public Department getDepartment() {
        return department;
    }

    public String getDepartmentCode() {
        return department.getCode();
    }

    public User getFaculty() {
        return faculty;
    }

    public int getCredits() {
        return credits;
    }

    public String getSemester() {
        return semester;
    }

    public int getClassesHeld() {
        return classesHeld;
    }
}
