package com._team._team.evaluation.exception;

import com._team._team.dto.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@Getter
public class SeasonActivationBlockedException extends BusinessException {

    private final List<UUID> inactiveMembers;
    private final List<UUID> weightShortageMembers;
    private final List<UUID> pendingBundleMembers;
    private final List<UUID> missingGoalsMembers;

    public SeasonActivationBlockedException(List<UUID> inactiveMembers,
                                            List<UUID> weightShortageMembers,
                                            List<UUID> pendingBundleMembers,
                                            List<UUID> missingGoalsMembers) {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                buildMessage(inactiveMembers, weightShortageMembers, pendingBundleMembers, missingGoalsMembers)
        );
        this.inactiveMembers = inactiveMembers;
        this.weightShortageMembers = weightShortageMembers;
        this.pendingBundleMembers = pendingBundleMembers;
        this.missingGoalsMembers = missingGoalsMembers;
    }

    private static String buildMessage(List<UUID> inactiveMembers,
                                       List<UUID> weightShortageMembers,
                                       List<UUID> pendingBundleMembers,
                                       List<UUID> missingGoalsMembers) {
        return String.format(
                "Season activation blocked: inactive=%d, weightShortage=%d, pendingBundles=%d, missingGoals=%d",
                size(inactiveMembers),
                size(weightShortageMembers),
                size(pendingBundleMembers),
                size(missingGoalsMembers)
        );
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
