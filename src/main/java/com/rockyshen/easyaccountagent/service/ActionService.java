package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dao.ActionDao;
import com.rockyshen.easyaccountagent.entity.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionService {

    private final ActionDao actionDao;

    @Transactional(readOnly = true)
    public List<Action> getActions() {
        return actionDao.findAll();
    }

    @Transactional(readOnly = true)
    public Action getAction(int id) {
        return actionDao.findById(id);
    }
}
