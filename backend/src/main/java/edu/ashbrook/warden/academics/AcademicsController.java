package edu.ashbrook.warden.academics;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.academics.AcademicDtos.AcademicSummary;
import edu.ashbrook.warden.academics.AcademicDtos.CourseDto;
import edu.ashbrook.warden.academics.AcademicDtos.GradeChangeRequest;
import edu.ashbrook.warden.academics.AcademicDtos.RosterRow;
import edu.ashbrook.warden.approval.ActionRequestDto;
import edu.ashbrook.warden.common.FieldValidationException;

/** Thin HTTP layer: authorization lives on the service methods, shared with the AI tools. */
@RestController
public class AcademicsController {

    private final AcademicsService academics;
    private final CourseService courses;

    public AcademicsController(AcademicsService academics, CourseService courses) {
        this.academics = academics;
        this.courses = courses;
    }

    @GetMapping("/api/academics/me")
    AcademicSummary mine() {
        return academics.mySummary();
    }

    @GetMapping("/api/courses")
    List<CourseDto> courses() {
        return courses.list();
    }

    @GetMapping("/api/courses/{code}/roster")
    List<RosterRow> roster(@PathVariable String code) {
        return courses.roster(code);
    }

    @PostMapping("/api/courses/{code}/grade-changes")
    @ResponseStatus(HttpStatus.CREATED)
    ActionRequestDto requestGradeChange(@PathVariable String code, @RequestBody GradeChangeRequest body) {
        if (body.enrollmentId() == null) {
            throw new FieldValidationException("enrollmentId", "Choose a student.");
        }
        return courses.requestGradeChange(code, body.enrollmentId(), body.newGrade(), body.reason());
    }
}
