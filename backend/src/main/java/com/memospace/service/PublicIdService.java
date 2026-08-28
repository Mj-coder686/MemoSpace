package com.memospace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.memospace.domain.UserAccount;
import com.memospace.domain.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class PublicIdService {
    private static final long MIN_ID = 100_000_000_000L;
    private static final long ID_RANGE = 900_000_000_000L;
    private static final int MAX_ATTEMPTS = 24;

    private final SecureRandom random = new SecureRandom();
    private final UserMapper users;

    public PublicIdService(UserMapper users) {
        this.users = users;
    }

    /** Inserts a new account with an opaque, fixed 12-digit Memo ID. */
    public void insertWithPublicId(UserAccount user) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            user.setPublicId(nextCandidate());
            try {
                users.insert(user);
                return;
            } catch (DuplicateKeyException duplicate) {
                // A username conflict must retain its normal meaning. Only Memo ID collisions are retried.
                if (users.selectCount(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, user.getUsername())) > 0) {
                    throw duplicate;
                }
                user.setId(null);
            }
        }
        throw new IllegalStateException("无法分配唯一的 Memo ID");
    }

    public String nextCandidate() {
        return Long.toString(MIN_ID + random.nextLong(ID_RANGE));
    }
}
