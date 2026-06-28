package com.example.im.repo;

import com.example.im.domain.ReplayJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReplayJobRepo {

    private final ReplayJobMapper mapper;

    public ReplayJob save(ReplayJob j) {
        if (j.getId() == null) {
            mapper.insert(j);
        } else {
            mapper.updateById(j);
        }
        return j;
    }

    public List<ReplayJob> findBySession(Long sessionId) {
        return mapper.findBySession(sessionId);
    }

    public ReplayJob findLatestBySession(Long sessionId) {
        return mapper.findLatestBySession(sessionId);
    }

    public ReplayJob findById(Long id) {
        return mapper.findById(id);
    }
}