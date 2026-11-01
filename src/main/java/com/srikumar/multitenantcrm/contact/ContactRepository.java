package com.srikumar.multitenantcrm.contact;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @Modifying
    @Query("update Contact c set c.name = :name where c.id = :id")
    int renameById(@Param("id") UUID id, @Param("name") String name);

    @Modifying
    @Query("delete from Contact c where c.id = :id")
    int deleteByIdReturningCount(@Param("id") UUID id);
}
