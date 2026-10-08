package edu.ashbrook.warden.dutyleave;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.StatusFilter;

@RestController
@RequestMapping("/api/duty-leave")
public class DutyLeaveController {

    public record ApplyRequest(LocalDate startDate, LocalDate endDate, String eventName, String reason) {
    }

    public record DecideRequest(Boolean approve, String note) {
    }

    private final DutyLeaveService dutyLeave;

    public DutyLeaveController(DutyLeaveService dutyLeave) {
        this.dutyLeave = dutyLeave;
    }

    @GetMapping("/mine")
    List<DutyLeaveDto> mine() {
        return dutyLeave.mine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DutyLeaveDto apply(@RequestBody ApplyRequest body) {
        return dutyLeave.apply(body.startDate(), body.endDate(), body.eventName(), body.reason()).leave();
    }

    @PostMapping("/{id}/cancel")
    DutyLeaveDto cancel(@PathVariable Long id) {
        return dutyLeave.cancel(id);
    }

    @GetMapping("/review")
    List<DutyLeaveDto> review(@RequestParam(defaultValue = "PENDING") String status) {
        return dutyLeave.reviewQueue(StatusFilter.pendingOnly(status));
    }

    @PostMapping("/{id}/decide")
    DutyLeaveDto decide(@PathVariable Long id, @RequestBody DecideRequest body) {
        if (body.approve() == null) {
            throw new FieldValidationException("approve", "Choose approve or reject.");
        }
        return dutyLeave.decide(id, body.approve(), body.note());
    }
}
