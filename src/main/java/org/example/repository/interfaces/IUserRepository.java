package org.example.repository.interfaces;

import org.example.domain.entitiesAssociatedWithUser.User;
import org.example.repository.interfaces.Repository;

import java.util.Optional;

public interface IUserRepository extends Repository<Long, User> {
    public Optional<User> findUserByCredentials(User potentialUser);
}
