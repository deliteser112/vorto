/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.vorto.repository.server.it;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.eclipse.vorto.repository.domain.NamespaceRole;
import org.eclipse.vorto.repository.domain.User;
import org.eclipse.vorto.repository.oauth.IOAuthProviderRegistry;
import org.eclipse.vorto.repository.services.UserBuilder;
import org.eclipse.vorto.repository.web.account.dto.UserDto;
import org.eclipse.vorto.repository.web.api.v1.dto.Collaborator;
import org.eclipse.vorto.repository.web.api.v1.dto.NamespaceDto;
import org.eclipse.vorto.repository.web.api.v1.dto.OperationResult;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * This class augments the test coverage on the NamespaceController. <br/>
 * The tests cover simple operations on namespaces, as well as collaborators, with a few edge
 * cases.
 */
public class NamespaceControllerIntegrationTest extends IntegrationTestBase {

  /**
   * Uses a non-compliant namespace notation
   *
   * @throws Exception
   */
  @Test
  public void testNamespaceCreationWithInvalidNotation() throws Exception {
    String badNamespaceName = "badNamespace==name!";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", badNamespaceName))
                .contentType("application/json").with(userModelCreator)
        )
        .andExpect(status().isBadRequest())
        .andExpect(
            content().json(
                objectMapper.writeValueAsString(
                    OperationResult.failure(String
                        .format("[%s] is not a valid namespace name - aborting namespace creation.",
                            badNamespaceName))
                )
            )
        );
  }

  /**
   * Creating a namespace with uppercase characters will succeed, but it will be lowercased. <br/>
   * Also uses underscores in namespaces.
   * @throws Exception
   */
  @Test
  public void testNSCreationWithUppercaseCharacters() throws Exception {
    String namespaceWithUppercaseCharacters = "com.Some_Other_Company.OFFICIA1";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isCreated());

    repositoryServer
        .perform(
            get(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(namespaceWithUppercaseCharacters.toLowerCase()));

    // cleanup
    repositoryServer
        .perform(
            delete(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isNoContent());
  }

  /**
   * While upper case characters are allowed while creating a namespace, its name will be lowercased
   * in the back-end upon persisting. <br/>
   * Therefore, trying to create two namespaces with the same name save for case will cause a
   * collision and fail.<br/>
   * The cleanup phase of this test that deletes the namespace also references it by the second
   * notation, thus ensuring that one can delete a namespace by name even if the name is not
   * lowercase, as it was persisted.<br/>
   * Also uses underscores in namespaces.
   *
   * @throws Exception
   */
  @Test
  public void produceNamespaceCollisionDueToCaseInsensitivity() throws Exception {
    String namespaceWithUppercaseCharacters = "com.Some_Other_Company.OFFICIA1";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isCreated());
    String sameNamespaceWithDifferentUCChars = "COM.some_Other_Company.officia1";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isConflict());

    // cleanup with a twist: tries to clean up by referencing the namespace name with different
    // casing
    repositoryServer
        .perform(
            delete(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isNoContent());

  }

  /**
   * This verifies that getting the collaborator of a namespace referenced with non-lowercase
   * notation still resolves the namespace as lowercase, as it was persisted upon creation.<br/>
   * Also uses underscores in namespaces.
   *
   * @throws Exception
   */
  @Test
  public void getCollaboratorsWithDifferentCasing() throws Exception {
    String namespaceWithUppercaseCharacters = "com.Some_Other_Company.OFFICIA1";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isCreated());

    String differentCasing = "CoM.sOME_oThEr_CoMpAnY.oFfIcIa1";
    repositoryServer
        .perform(
            get(String.format("/rest/namespaces/%s/users", differentCasing))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(1)));

    // cleanup
    repositoryServer
        .perform(
            delete(String.format("/rest/namespaces/%s", namespaceWithUppercaseCharacters))
                .contentType("application/json").with(userSysadmin)
        )
        .andExpect(status().isNoContent());
  }

  /**
   * Uses an empty namespace name after trimming
   *
   * @throws Exception
   */
  @Test
  public void testNamespaceCreationWithEmptyNamespace() throws Exception {
    repositoryServer
        .perform(
            put("/rest/namespaces/ \t")
                .contentType("application/json").with(userModelCreator)
        )
        .andExpect(status().isBadRequest())
        .andExpect(
            content().json(
                objectMapper.writeValueAsString(OperationResult
                    .failure("Namespace name is empty - aborting namespace creation."))
            )
        );
  }

  /**
   * Only sysadmins can create namespaces that don't start with {@literal vorto.private.}
   *
   * @throws Exception
   */
  @Test
  public void testNamespaceCreationWithoutPrivatePrefixAsNonSysadmin() throws Exception {
    String namespaceName = "myNamespace";
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s", namespaceName))
                .contentType("application/json").with(userModelCreator)
        )
        .andExpect(status().isBadRequest())
        .andExpect(
            content().json(
                objectMapper.writeValueAsString(
                    OperationResult.failure(String.format(
                        "[%s] is an invalid name for a private namespace - aborting namespace creation.",
                        namespaceName)))
            )
        );
  }

  /**
   * Simply tests that creating a namespace with the proper prefix for non-sysadmin users works.
   *
   * @throws Exception
   */
  @Test
  public void testNamespaceCreationWithPrivatePrefixAsNonSysadmin() throws Exception {
    createNamespaceSuccessfully("vorto.private.myNamespace", userModelCreator);
  }

  /**
   * Simply tests that creating a namespace with no prefix for sysadmin users works.
   *
   * @throws Exception
   */
  @Test
  public void testNamespaceCreationWithNoPrefixAsSysadmin() throws Exception {
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);
  }

  /**
   * Tests that creating {@literal x} namespaces for non-sysadmin users fails, where {@literal x > config.restrictTenant}.
   * <br/>
   * Note that {@literal config.restrictTenant} is set to {@literal 1} in the production profile,
   * but {@literal 2} in test for some reason.
   *
   * @throws Exception
   */
  @Test
  public void testMultipleNamespaceCreationAsNonSysadmin() throws Exception {
    int maxNamespaces = -1;
    try {
      maxNamespaces = Integer.valueOf(privateNamespaceQuota);
    } catch (NumberFormatException | NullPointerException e) {
      fail("restrictTenant configuration value not available within context.");
    }

    for (int i = 0; i < maxNamespaces; i++) {
      createNamespaceSuccessfully(String.format("vorto.private.myNamespace%d", i),
          userModelCreator);
    }

    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/vorto.private.myNamespace%d", maxNamespaces))
                .contentType("application/json").with(userModelCreator)
        )
        .andExpect(status().isForbidden())
        .andExpect(
            content().json(
                // hard-coded error message due to production profile limiting to 1 namespace for
                // non-sysadmins
                objectMapper.writeValueAsString(
                    OperationResult.failure(
                        String.format(
                            "User already has reached quota [%d] of private namespaces - aborting namespace creation.",
                            maxNamespaces
                        )
                    )
                )
            )
        );
  }

  /**
   * Tests that creating {@literal x} namespaces for sysadmin users succeeds even when
   * {@literal x > config.restrictTenant}.
   *
   * @throws Exception
   */
  @Test
  public void testMultipleNamespaceCreationAsSysadmin() throws Exception {
    int maxNamespaces = -1;
    try {
      maxNamespaces = Integer.valueOf(privateNamespaceQuota);
    } catch (NumberFormatException | NullPointerException e) {
      fail("restrictTenant configuration value not available within context.");
    }

    for (int i = 0; i < maxNamespaces; i++) {
      createNamespaceSuccessfully(String.format("myNamespace%d", i), userSysadmin);
    }

    createNamespaceSuccessfully(String.format("myNamespace%d", maxNamespaces), userSysadmin);
  }

  /**
   * Simply tests that adding an existing user to a namespace with a basic role works as intended.
   *
   * @throws Exception
   */
  @Test
  public void testAddNamespaceUserWithOneRole() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the creator user
    Collaborator userModelCreatorCollaborator = new Collaborator();
    // you would think a user called "userModelCreator" has a username called "userModelCreator", but
    // the way it has been mapped to in the parent class is "user3" instead
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "true"
            )
        );
  }

  /**
   * Tests that adding an non-existing user to a namespace fails
   *
   * @throws Exception
   */
  @Test
  public void testAddNamespaceNonExistingUser() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the non-existing user
    Collaborator nonExistingCollaborator = new Collaborator();
    nonExistingCollaborator.setUserId("toto");
    nonExistingCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    nonExistingCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(nonExistingCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isNotFound())

        .andExpect(
            content().string(
                "false"
            )
        );

  }

  /**
   * Tests that removing an existing user from a namespace works if the user has been added previously
   * and the user removing it has the rights to do so.
   *
   * @throws Exception
   */
  @Test
  public void testRemoveExistingUserFromNamespace() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the creator user
    Collaborator userModelCreatorCollaborator = new Collaborator();
    // you would think a user called "userModelCreator" has a username called "userModelCreator", but
    // the way it has been mapped to in the parent class is "user3" instead
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );
    // now removes the user
    repositoryServer
        .perform(
            delete("/rest/namespaces/myAdminNamespace/users")
                .param("username", USER_MODEL_CREATOR_NAME)
                .param("authenticationProvider", GITHUB)
                .with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "true"
            )
        );
  }

  /**
   * Tests that removing a non-existing user from a namespace fails.<br/>
   * Note that the response will just return "false" if no user has been removed here.
   *
   * @throws Exception
   */
  @Test
  public void testRemoveUserFromNamespaceWhereUserIsNotACollaborator() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // now removes a user that has not been added
    repositoryServer
        .perform(
            delete("/rest/namespaces/myAdminNamespace/users")
                .param("username", USER_MODEL_CREATOR_NAME)
                .param("authenticationProvider", GITHUB)
                .with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "false"
            )
        );
  }

  /**
   * Tests that removing an existing user from a namespace fails if the user performing the operation
   * has no "tenant admin" role for that namespace. <br/>
   * In this case, we're creating a third user with tenant admin authority, who is not added as
   * admin of that namespace, and will try to remove the simple user.<br/>
   * Note that it might be worth thinking of the edge case where a user simply wants to be removed
   * from a namespace they have been added to, regardless of their role in that namespace. <br/>
   * See {@link org.eclipse.vorto.repository.web.api.v1.NamespaceController#removeUserFromNamespace(String, UserDto)}
   * for specifications on how authorization is enforced.
   *
   * @throws Exception
   */
  @Test
  public void testRemoveExistingUserFromNamespaceWithNoPrivileges() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);

    // adds the collaborator with "model_viewer" roles to the namespace
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );

    // creates a user with namespace admin privileges in a different namespace
    SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor thirdUser = user("thirdPartyUser")
        .password("pass");
    userRepository.save(
        new UserBuilder()
            .withAuthenticationProviderID(GITHUB)
            .withName("thirdPartyUser").build()
    );

    // finally removes the user from the namespace but with the "thirdPartyUser" who is tenant admin
    // "somewhere else", which fails due to lack of tenant admin role on that given namespace
    repositoryServer
        .perform(
            delete("/rest/namespaces/myAdminNamespace/users")
                .param("username", USER_MODEL_CREATOR_NAME)
                .param("authenticationProvider", GITHUB)
                .with(thirdUser)
        )
        .andExpect(status().isForbidden());
  }

  /**
   * Tests that changing roles on a namespace works for an existing user who has been previously
   * added to that namespace.
   *
   * @throws Exception
   */
  @Test
  public void testChangeUserRolesOnNamespaceUser() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the creator user
    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );
    // finally, change the user roles on the DTO and PUT again
    roles.add("model_creator");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );
  }

  /**
   * Tests that changing a user's roles on a namespace from a user who's tenant admin, but not on
   * that namespace, fails.
   *
   * @throws Exception
   */
  @Test
  public void testChangeUserRolesOnNamespaceUserWithExtraneousUser() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the creator user
    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );

    // creates a user with tenant admin privileges but no access to the namespace in question
    SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor thirdUser = user("thirdPartyUser")
        .password("pass");
    userRepository.save(
        new UserBuilder()
            .withAuthenticationProviderID(GITHUB)
            .withName("thirdPartyUser").build()
    );
    // finally, change the user roles on the DTO and PUT again
    roles.add("model_creator");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(thirdUser)
        )
        .andExpect(status().isForbidden());
  }

  /**
   * Tests that checking whether the logged on user has a given role on a given namespace works as
   * expected.<br/>
   * The endpoint is a simplification of the former TenantService.js all deferred to the back-end,
   * and is used contextually to verifying whether a user can modify a model.
   *
   * @throws Exception
   */
  @Test
  public void testHasRoleOnNamespace() throws Exception {
    // first, creates the namespace for the admin user
    createNamespaceSuccessfully("myAdminNamespace", userSysadmin);

    // then, add the creator user
    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/myAdminNamespace/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );
    // now checks whether the user has model_viewer role
    repositoryServer
        .perform(
            get("/rest/namespaces/model_viewer/myAdminNamespace")
                .with(userModelCreator)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "true"
            )
        );
    // finally, checks whether the user has model_creator role, which they haven't
    repositoryServer
        .perform(
            get("/rest/namespaces/model_creator/myAdminNamespace")
                .with(userModelCreator)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "false"
            )
        );
  }

  /**
   * Tests that checking whether the logged on user is the only admin on any of their namespaces
   * returns as expected. <br/>
   * The endpoint is a simplification of the former TenantService.js all deferred to the back-end,
   * and is used contextually to a user trying to delete their account.
   *
   * @throws Exception
   */
  @Test
  public void testIsOnlyAdminOnAnyNamespace() throws Exception {
    String namespaceName = "myAdminNamespace";
    // first, creates the namespace
    createNamespaceSuccessfully(namespaceName, userSysadmin);

    // now checks whether the creator user is the only admin user of any namespace - since they
    // only have one, this will return true
    repositoryServer
        .perform(
            get("/rest/namespaces/userIsOnlyAdmin")
                .with(userSysadmin)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "true"
            )
        );

    /*
    Now adds another user as tenant admin for the namespace.
    Note: this is done with the admin user here, because of the pre-authorization checks in the
    controller, that verify if a user has the Spring role at all.
    Since those users are mocked and their roles cannot be changed during tests, the userModelCreator
    user would fail to add a collaborator at this point (but not in real life, since they would be
    made tenant admin of the namespace they just created).
    */
    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_VIEWER_NAME);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    roles.add("namespace_admin");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put(String.format("/rest/namespaces/%s/users", namespaceName))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );
    // finally, checks whether the original user is still only admin in any of their namespaces -
    // which they aren't now, since we've added another user with tenant admin privileges
    repositoryServer
        .perform(
            get("/rest/namespaces/userIsOnlyAdmin")
                .with(userModelCreator)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                "false"
            )
        );
  }

  /**
   * Verifies the list of namespaces where the logged on user has a given role is correct.
   *
   * @throws Exception
   */
  @Test
  public void testAccessibleNamespacesWithRole() throws Exception {
    // first, creates a namespace for the userModelCreator user
    createNamespaceSuccessfully("vorto.private.myNamespace", userModelCreator2);

    // now, creates a namespace for the userModelCreator2 user
    createNamespaceSuccessfully("vorto.private.myNamespace2", userModelCreator3);

    // Now adds userModelCreator to userModelCreator2's namespace as model creator
    Collaborator userModelCreatorCollaborator = new Collaborator();
    userModelCreatorCollaborator.setUserId(USER_MODEL_CREATOR_NAME_2);
    userModelCreatorCollaborator.setAuthenticationProviderId(GITHUB);
    userModelCreatorCollaborator.setSubject("none");
    Set<String> roles = new HashSet<>();
    roles.add("model_viewer");
    roles.add("model_creator");
    userModelCreatorCollaborator.setRoles(roles);
    repositoryServer
        .perform(
            put("/rest/namespaces/vorto.private.myNamespace2/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(userModelCreatorCollaborator))
                .with(userSysadmin)
        )
        .andExpect(status().isOk())

        .andExpect(
            content().string(
                "true"
            )
        );

    // finally, lists all namespaces where userModelCreator has the model_creator role, that is, their own
    // and userModelCreator2's namespace

    // expected namespaces
    Collection<NamespaceDto> expectedNamespaces = new ArrayList<>();

    // admins and users of the userModelCreator's namespace
    Collection<Collaborator> userModelCreatorNSAdmins = new ArrayList<>();
    Collection<Collaborator> userModelCreatorNSUsers = new ArrayList<>();

    /*
    Creating set of namespace owner roles
    */
    Set<String> ownerRoles = new HashSet<>();
    ownerRoles.add("model_viewer");
    ownerRoles.add("model_creator");
    ownerRoles.add("namespace_admin");
    ownerRoles.add("model_promoter");
    ownerRoles.add("model_reviewer");
    ownerRoles.add("model_publisher");

    // creating Collaborator for userModelCreator as admin in their own namespace
    Collaborator userModelCreatorCollaboratorAsAdmin = new Collaborator();
    userModelCreatorCollaboratorAsAdmin.setUserId(USER_MODEL_CREATOR_NAME_2);
    userModelCreatorCollaboratorAsAdmin.setAuthenticationProviderId(GITHUB);
    userModelCreatorCollaboratorAsAdmin.setSubject("none");
    userModelCreatorCollaboratorAsAdmin.setRoles(ownerRoles);
    userModelCreatorNSAdmins.add(userModelCreatorCollaboratorAsAdmin);

    // creating Collaborator for userModelCreator as user in their own namespace
    Collaborator userModelCreatorCollaboratorAsUserSysadmin = new Collaborator();
    userModelCreatorCollaboratorAsUserSysadmin.setUserId(USER_MODEL_CREATOR_NAME_2);
    userModelCreatorCollaboratorAsUserSysadmin.setAuthenticationProviderId(GITHUB);
    userModelCreatorCollaboratorAsUserSysadmin.setSubject("none");
    userModelCreatorCollaboratorAsUserSysadmin.setRoles(ownerRoles);
    userModelCreatorNSUsers.add(userModelCreatorCollaboratorAsUserSysadmin);

    // creating namespace for userModelCreator - lowercased ns name
    NamespaceDto userModelCreatorNS = new NamespaceDto("vorto.private.myNamespace".toLowerCase(),
        userModelCreatorNSUsers, userModelCreatorNSAdmins);

    // creating userModelCreator2 as a Collaborator object
    Collaborator userModelCreator2CollaboratorAsAdmin = new Collaborator();
    userModelCreator2CollaboratorAsAdmin.setUserId(USER_MODEL_CREATOR_NAME_3);
    userModelCreator2CollaboratorAsAdmin.setAuthenticationProviderId(GITHUB);
    userModelCreator2CollaboratorAsAdmin.setSubject("none");
    userModelCreator2CollaboratorAsAdmin.setRoles(ownerRoles);

    Collaborator userModelCreator2CollaboratorAsUserSysadmin = new Collaborator();
    userModelCreator2CollaboratorAsUserSysadmin.setUserId(USER_MODEL_CREATOR_NAME_3);
    userModelCreator2CollaboratorAsUserSysadmin.setAuthenticationProviderId(GITHUB);
    userModelCreator2CollaboratorAsUserSysadmin.setSubject("none");
    userModelCreator2CollaboratorAsUserSysadmin.setRoles(ownerRoles);

    // adding to userModelCreator2 namespace admins
    Collection<Collaborator> userModelCreator2NSAdmins = new ArrayList<>();
    userModelCreator2NSAdmins.add(userModelCreator2CollaboratorAsAdmin);

    // adding both userModelCreator2 collaborator and userModelCreator (the non-admin collaborator from up above)
    // to the userModelCreator2 namespace users
    Collection<Collaborator> userModelCreator2NSUsers = new ArrayList<>();
    userModelCreator2NSUsers.add(userModelCreator2CollaboratorAsUserSysadmin);
    userModelCreator2NSUsers.add(userModelCreatorCollaborator);

    // creating ns for userModelCreator2 - namespace name lowercased
    NamespaceDto userModelCreator2NS = new NamespaceDto("vorto.private.myNamespace2".toLowerCase(),
        userModelCreator2NSUsers, userModelCreator2NSAdmins);

    // adding both ns to expected collection
    expectedNamespaces.add(userModelCreatorNS);
    expectedNamespaces.add(userModelCreator2NS);

    repositoryServer
        .perform(
            get("/rest/namespaces/role/model_creator")
                .with(userModelCreator2)
        )
        .andExpect(status().isOk())
        .andExpect(
            content().json(objectMapper.writeValueAsString(expectedNamespaces))
        );

  }

  // --- tests below adapted from former NamespaceControllerIntegrationTest ---

  @Test
  public void getNamespacesNoneFound() throws Exception {
    repositoryServer.perform(get("/rest/namespaces").with(userSysadmin))
        .andExpect(status().isNotFound());
  }

  @Test
  public void getNamespacesFound() throws Exception {
    createNamespaceSuccessfully("myNamespace", userSysadmin);
    repositoryServer.perform(get("/rest/namespaces/all").with(userSysadmin))
        .andExpect(status().isOk());
  }

  @Test
  public void getNamespaceNotAuthorized() throws Exception {
    createNamespaceSuccessfully("myNamespace", userSysadmin);
    repositoryServer.perform(get("/rest/namespaces/myNamespace").with(userModelCreator))
        .andExpect(status().isForbidden());
  }

  @Test
  public void getNamespace() throws Exception {
    repositoryServer.perform(get("/rest/namespaces/com.mycompany").with(userSysadmin))
        .andExpect(status().isOk());
  }

  @Test
  public void updateCollaborator() throws Exception {
    // creates and adds a collaborator
    Collaborator collaborator = new Collaborator(USER_MODEL_CREATOR_NAME, GITHUB, "none",
        Lists.newArrayList("model_viewer", "model_creator"));
    repositoryServer.perform(
        put("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isOk());

    // checks the collaborator's roles are returned correctly
    checkCollaboratorRoles("com.mycompany", USER_MODEL_CREATOR_NAME, "model_viewer",
        "model_creator");

    // creates and adds another collaborator with different roles
    collaborator = new Collaborator(USER_MODEL_VIEWER_NAME, GITHUB, "none",
        Lists.newArrayList("model_viewer"));
    repositoryServer.perform(
        put("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isOk());

    // checks the collaborator's roles are returned correctly
    checkCollaboratorRoles("com.mycompany", USER_MODEL_VIEWER_NAME, "model_viewer");
  }

  @Test
  public void updateCollaboratorAddTechnicalUser() throws Exception {

    String namespaceName = "com.mycompany";

    Collaborator collaborator = new Collaborator("my-technical-user", GITHUB, "ProjectX",
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);
    createTechnicalUserAndAddToNamespace(namespaceName, collaborator);

    User technicalUser = userRepository.findByUsernameAndAuthenticationProviderId("my-technical-user", GITHUB).get();
    assertNotNull(technicalUser);
    assertTrue(technicalUser.isTechnicalUser());

    checkCollaboratorRoles(namespaceName, "my-technical-user", "model_viewer", "model_creator");

    collaborator = new Collaborator("my-technical-user", GITHUB, "ProjectX",
        Lists.newArrayList("model_viewer"));

    // cannot re-create tech user so adding as collaborator since it already exists now
    addCollaboratorToNamespace(namespaceName, collaborator);

    checkCollaboratorRoles(namespaceName, "my-technical-user", "model_viewer");
  }

  @Test
  public void updateCollaboratorWithoutPermissions() throws Exception {
    // creates user and collaborator to add
    String otherUser = "userstandard2";
    Collaborator collaborator = new Collaborator(otherUser, GITHUB, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    userRepository
        .save(new UserBuilder().withName(otherUser).withAuthenticationProviderID(GITHUB).build());
    // tries to add other user as yet another user, who has no rights to that namespace
    repositoryServer.perform(
        put(String.format("/rest/namespaces/%s/users", "com.mycompany"))
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userModelViewer))
        .andExpect(status().isForbidden());
  }

  /**
   * The difference between POST and PUT operations on collaborators essentially boils down to:
   * <ul>
   *   <li>
   *     POST ops mean creating a new technical user, and therefore validate the whole collaborator
   *     payload but assume the user does not exist.
   *   </li>
   *   <li>
   *     PUT ops mean adding an existing user to the namespace, and therefore only perform minimal
   *     validation (i.e. the user name and authentication provider), yet assume the user exists
   *     and fail otherwise.
   *   </li>
   *   <li>
   *     In this case, since users are now identified by both the username and the OAuth provider
   *     ID, the PUT operation fails with 404.
   *   </li>
   * </ul>
   *
   * @throws Exception
   */
  @Test
  public void updateCollaboratorUnknownProvider() throws Exception {
    // creates the collaborator payload and the existing user
    String otherUser = "userstandard2";
    Collaborator collaborator = new Collaborator(otherUser, "unknownProvider", null,
        Lists.newArrayList("model_viewer", "model_creator"));
    userRepository
        .save(new UserBuilder().withName(otherUser).withAuthenticationProviderID(GITHUB).build());
    repositoryServer.perform(
        put("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isNotFound());
  }

  @Test
  public void createTechnicalCollaboratorUnknownProvider() throws Exception {
    // creates the collaborator payload and the existing user
    String otherUser = "userstandard2";
    Collaborator collaborator = new Collaborator(otherUser, "unknownProvider", null,
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);

    repositoryServer.perform(
        post("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTechnicalCollaboratorNoProvider() throws Exception {
    // creates the collaborator payload and the existing user
    String otherUser = "userstandard2";
    Collaborator collaborator = new Collaborator(otherUser, null, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);

    repositoryServer.perform(
        post("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void updateCollaboratorNoSubject() throws Exception {
    // creates the collaborator payload and the existing user
    String otherUser = "userstandard2";
    Collaborator collaborator = new Collaborator(otherUser, GITHUB, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    userRepository
        .save(new UserBuilder().withName(otherUser).withAuthenticationProviderID(GITHUB).build());
    repositoryServer.perform(
        put("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isOk());
  }

  @Test
  public void createTechnicalCollaboratorNoSubject() throws Exception {
    Collaborator collaborator = new Collaborator("my-technical-user", BOSCH_IOT_SUITE_AUTH, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);

    repositoryServer.perform(
        post("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTechnicalCollaboratorSubjectTooShort() throws Exception {
    Collaborator collaborator = new Collaborator("my-technical-user", BOSCH_IOT_SUITE_AUTH, "abc",
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);

    repositoryServer.perform(
        post("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTechnicalCollaboratorSubjectNotAlnum() throws Exception {
    Collaborator collaborator = new Collaborator("my-technical-user", BOSCH_IOT_SUITE_AUTH, "$%&/$",
        Lists.newArrayList("model_viewer", "model_creator"));
    collaborator.setTechnicalUser(true);

    repositoryServer.perform(
        post("/rest/namespaces/com.mycompany/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void updateCollaboratorUserDoesNotExist() throws Exception {
    String namespaceName = "com.mycompany";

    Collaborator collaborator = new Collaborator("unknownUser", GITHUB, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    repositoryServer.perform(
        put(String.format("/rest/namespaces/%s/users", namespaceName))
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isNotFound())
        .andExpect(content().string("false"));
  }

  @Test
  public void updateCollaboratorNamespaceDoesNotExist() throws Exception {
    Collaborator collaborator = new Collaborator(USER_MODEL_CREATOR_NAME, GITHUB, null,
        Lists.newArrayList("model_viewer", "model_creator"));
    repositoryServer.perform(
        put("/rest/namespaces/com.unknown.namespace/users")
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isNotFound());
  }

  @Test
  public void deleteExistingNamespace() throws Exception {
    String namespaceName = "vorto.private.mynamespace";
    createNamespaceSuccessfully(namespaceName, userModelCreator);

    // deletes
    repositoryServer.perform(
        delete(String.format("/rest/namespaces/%s", namespaceName))
            .with(userModelCreator)
    )

        .andExpect(status().isNoContent());
  }

  @Test
  public void deleteNonExistingNamespace() throws Exception {
    repositoryServer.perform(
        delete("/rest/namespaces/nonExistingNamespace")
            .with(userSysadmin)
    )
        .andExpect(status().isNotFound());
  }

  @Test
  public void deleteExistingNamespaceNotAuthorized() throws Exception {
    String namespaceName = "myNamespace";
    createNamespaceSuccessfully(namespaceName, userSysadmin);
    repositoryServer.perform(
        delete(String.format("/rest/namespaces/%s", namespaceName))
            .with(userModelCreator)
    )

        .andExpect(status().isForbidden());
  }

  // newer tests

  /**
   * Verifies that the set of namespaces retrieved by searching for all accessible namespaces by a
   * partial substring of the name for a given user returns both all public namespaces with partial
   * matches, and all private namespaces with partial matches (where the user has at least one role).<br/>
   * This is used by the "request access to namespace" form.
   *
   * @throws Exception
   */
  @Test
  public void testFindAllAccessibleNamespacesByPartial() throws Exception {
    // creates a public namespace as sysadmin and adds userModelCreator as collaborator
    String publicNamespaceName = "org.publicnamespace.abcd";
    createNamespaceSuccessfully(publicNamespaceName, userSysadmin);
    Collaborator collaborator = new Collaborator("userModelCreator", "GITHUB", null,
        Lists.newArrayList("model_viewer", "model_creator"));
    repositoryServer.perform(
        put(String.format("/rest/namespaces/%s/users", publicNamespaceName))
            .content(objectMapper.writeValueAsString(collaborator))
            .contentType(MediaType.APPLICATION_JSON)
            .with(userSysadmin))
        .andExpect(status().isOk());
    // creates a private namespace where the searched name would match, but the userModelCreator has
    // no role
    createNamespaceSuccessfully("vorto.private.sysadmin.abcd", userSysadmin);
    // creates a private namespace for userModelCreator
    String privateNamespaceName = "vorto.private.mynamespace.abcd";
    createNamespaceSuccessfully(privateNamespaceName, userModelCreator);

    // now compares expected namespaces to appear in search with REST endpoint outcome
    // note that users and admins in returned DTOs are empty here by design
    // also note that the namespaces are sorted by name
    List<NamespaceDto> expectedNamespaces = Arrays.asList(
        new NamespaceDto(publicNamespaceName, Collections.emptyList(), Collections.emptyList()),
        new NamespaceDto(privateNamespaceName, Collections.emptyList(), Collections.emptyList())
    );
    repositoryServer.perform(
        get("/rest/namespaces/search/abcd")
            .with(userModelCreator)
    )
        .andExpect(status().isOk())
        .andExpect(content().json(objectMapper.writeValueAsString(expectedNamespaces)));
  }

  @Test
  public void verifySysadminCanAccessAllNamespaces() throws Exception {

    createNamespaceSuccessfully("vorto.private.creatorNS", userModelCreator);

    createNamespaceSuccessfully("vorto.private.creatorNS2", userModelCreator2);

    createNamespaceSuccessfully("adminNS", userSysadmin);

    repositoryServer.perform(
        get("/rest/namespaces/all")
            .with(userSysadmin)
    )
        .andExpect(status().isOk())
        // also counts the "com.mycompany" namespace created in other tests and never cleaned up
        // TODO better isolation
        .andExpect(jsonPath("$", hasSize(4)));
  }

  /**
   * This only tests that successive requests to create namespaces and models have no interference
   * from the request-scoped cached data. Arguably it should probably be moved somewhere else.
   *
   * @throws Exception
   */
  @Test
  public void testMultipleNSAndModelCreationAsSysadmin() throws Exception {
    String modelId0 = "com.test.Location:1.0.0";
    String fileName0 = "Location.fbmodel";
    createNamespaceSuccessfully("com.test", userSysadmin);
    createModel(userSysadmin, fileName0, modelId0);
    String modelId1 = "com.test2.Lamp2:1.0.0";
    String fileName1 = "Lamp2.fbmodel";
    createNamespaceSuccessfully("com.test2", userSysadmin);
    createModel(userSysadmin, fileName1, modelId1);
  }
}
