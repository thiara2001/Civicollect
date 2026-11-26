package com.civicollect.service;

import com.civicollect.domain.Need;
import com.civicollect.domain.NeedHistory;
import com.civicollect.domain.NeedStatus;
import com.civicollect.repository.NeedHistoryRepository;
import com.civicollect.repository.UserRepository;
import com.civicollect.security.AuthenticatedUser;
import com.civicollect.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historisation de toute modification de statut ou de priorité (traçabilité, RF31). */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final NeedHistoryRepository repository;
    private final CurrentUserProvider currentUser;
    private final UserRepository users;

    @Transactional
    public void record(Need need, String action, NeedStatus from, NeedStatus to, String details) {
        NeedHistory h = new NeedHistory();
        h.setNeed(need);
        h.setActor(currentUser.find().map(AuthenticatedUser::getId).map(users::getReferenceById).orElse(null));
        h.setAction(action);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setDetails(details);
        repository.save(h);
    }
}
