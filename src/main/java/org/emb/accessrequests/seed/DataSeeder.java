package org.emb.accessrequests.seed;

import org.emb.accessrequests.accesstype.entity.AccessType;
import org.emb.accessrequests.accesstype.repository.AccessTypeRepository;
import org.emb.accessrequests.user.entity.User;
import org.emb.accessrequests.user.enums.Role;
import org.emb.accessrequests.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts the contract's demo users and access types into an empty database.
 * Rows are inserted in the contract's order so the generated ids match it.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "password123";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final AccessTypeRepository accessTypes;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository users, AccessTypeRepository accessTypes, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.accessTypes = accessTypes;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        log.info("Empty database: inserting demo users and access types");

        user("admin", "Adam Admin", Role.ADMIN, null);
        User maria = user("maria", "Maria Manager", Role.MANAGER, null);
        User mark = user("mark", "Mark Manager", Role.MANAGER, null);
        user("alice", "Alice Anderson", Role.USER, maria);
        user("bob", "Bob Brown", Role.USER, maria);
        user("charlie", "Charlie Clark", Role.USER, mark);

        if (accessTypes.count() == 0) {
            accessType("VPN Access", "Remote connection to the internal company network.");
            accessType("GitHub / GitLab Access", "Access to the company source code repositories.");
            accessType("Figma Access", "Editor seat in the company design workspace.");
            accessType("Jira Access", "Create and manage issues in the project tracker.");
        }
    }

    private User user(String username, String fullName, Role role, User manager) {
        // Each hash gets its own salt, so encode per user.
        return users.saveAndFlush(new User(username, fullName, passwordEncoder.encode(DEMO_PASSWORD), role,
                manager == null ? null : manager.getId()));
    }

    private void accessType(String name, String description) {
        accessTypes.saveAndFlush(new AccessType(name, description));
    }
}
