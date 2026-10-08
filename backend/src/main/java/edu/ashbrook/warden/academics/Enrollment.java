package edu.ashbrook.warden.academics;

import edu.ashbrook.warden.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_code")
    private Course course;

    private int classesAttended;

    private int dutyLeaveCredited;

    private Grade grade;

    private boolean gradePublished;

    @Version
    private long version;

    protected Enrollment() {
    }

    public Enrollment(User student, Course course, int classesAttended, int dutyLeaveCredited, Grade grade,
                      boolean gradePublished) {
        this.student = student;
        this.course = course;
        this.classesAttended = classesAttended;
        this.dutyLeaveCredited = dutyLeaveCredited;
        this.grade = grade;
        this.gradePublished = gradePublished;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public int getClassesAttended() {
        return classesAttended;
    }

    public int getDutyLeaveCredited() {
        return dutyLeaveCredited;
    }

    public Grade getGrade() {
        return grade;
    }

    /** The grade a student or roster may see: only once published. */
    public Grade getPublishedGrade() {
        return gradePublished ? grade : null;
    }

    public boolean isGradePublished() {
        return gradePublished;
    }

    public void changeGrade(Grade newGrade) {
        this.grade = newGrade;
    }

    /** Credits up to {@code workingDays} classes, never past the number of classes actually missed. Returns the credit given. */
    public int creditDutyLeave(int workingDays) {
        int missed = course.getClassesHeld() - classesAttended - dutyLeaveCredited;
        int credit = Math.max(0, Math.min(workingDays, missed));
        dutyLeaveCredited += credit;
        return credit;
    }
}
