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
package org.eclipse.vorto.repository.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.eclipse.vorto.repository.domain.IRole;
import org.eclipse.vorto.repository.domain.Namespace;
import org.eclipse.vorto.repository.domain.Role;
import org.eclipse.vorto.repository.domain.User;
import org.eclipse.vorto.repository.domain.UserNamespaceID;
import org.eclipse.vorto.repository.domain.UserNamespaceRoles;
import org.eclipse.vorto.repository.repositories.NamespaceRepository;
import org.eclipse.vorto.repository.repositories.NamespaceRoleRepository;
import org.eclipse.vorto.repository.repositories.UserNamespaceRoleRepository;
import org.eclipse.vorto.repository.repositories.UserRepository;
import org.eclipse.vorto.repository.services.exceptions.DoesNotExistException;
import org.eclipse.vorto.repository.services.exceptions.InvalidUserException;
import org.eclipse.vorto.repository.services.exceptions.OperationForbiddenException;
import org.eclipse.vorto.repository.web.api.v1.dto.ICollaborator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;

/**
 * This service reports information and manipulates user roles on namespaces.<br/>
 * It is session-scoped, as any user with administrative privileges on a namespace can change
 * other users' access to it by means of roles.
 */
// TODO #2265: some validation operations could be reused by autowiring the NamespaceService, which may, however, introduce a Spring circular dependency
// TODO #2265: should operations on namespace roles trigger new application events?
// TODO #2265 not expose actor and take it from session
@Service
public class UserNamespaceRoleService implements ApplicationEventPublisherAware {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserNamespaceRoleService.class);

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private NamespaceRepository namespaceRepository;

  @Autowired
  private NamespaceRoleRepository namespaceRoleRepository;

  @Autowired
  private UserNamespaceRoleRepository userNamespaceRoleRepository;

  @Autowired
  private UserRepositoryRoleService userRepositoryRoleService;

  @Autowired
  private ServiceValidationUtil validator;

  @Autowired
  private RoleUtil roleUtil;

  @Autowired
  private UserUtil userUtil;

  @Autowired
  private UserService userService;

  private ApplicationEventPublisher eventPublisher;

  @Override
  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.eventPublisher = applicationEventPublisher;
  }

  // utility methods

  /**
   * The {@literal namespace_admin} role used in many authorization scenarios.
   *
   * @return
   */
  public IRole namespaceAdminRole() {
    return namespaceRoleRepository.find("namespace_admin");
  }

  public Collection<IRole> allRoles() {
    return namespaceRoleRepository.findAll().stream().map(r -> (IRole) r)
        .collect(Collectors.toSet());
  }

  /**
   * Verifies whether the given {@literal actor} {@link User} is either sysadmin, or has the
   * {@literal namespace_admin} role on the given {@link Namespace}.<br/>
   * Throws {@link OperationForbiddenException} if no condition above applies.
   *
   * @param actor
   * @param namespace
   * @throws OperationForbiddenException
   */
  public void authorizeActorAsAdminOnNamespace(User actor, Namespace namespace)
      throws OperationForbiddenException {
    // authorizing actor
    // not sysadmin
    if (!userRepositoryRoleService.isSysadmin(actor)) {
      // not namespace admin
      if (!hasRole(actor, namespace, namespaceAdminRole())) {
        throw new OperationForbiddenException(
            String.format(
                "Acting user is not authorized to manipulate namespace roles for target user on namespace [%s] - aborting operation.",
                namespace.getName()
            )
        );
      }
    }
  }

  /**
   * @param user
   * @param namespace
   * @param role
   * @return
   * @see org.eclipse.vorto.repository.account.impl.DefaultUserAccountService#hasRole(String, String, String)
   */
  public boolean hasRole(User user, Namespace namespace, IRole role) {
    validator.validateNulls(user, namespace, role);
    LOGGER.info(String
        .format("Verify whether user has role [%s] on namespace [%s]",
            role.getName(), namespace.getName()));
    if (!namespaceRoleRepository.findAll().contains(role)) {
      throw new IllegalArgumentException(String.format("Role [%s] is unknown", role.getName()));
    }
    UserNamespaceRoles userNamespaceRoles = userNamespaceRoleRepository
        .findOne(new UserNamespaceID(user, namespace));
    if (Objects.isNull(userNamespaceRoles)) {
      return false;
    }
    return (userNamespaceRoles.getRoles() & role.getRole()) == role.getRole();
  }

  /**
   * @param username
   * @param namespaceName
   * @param roleName
   * @return
   * @see UserNamespaceRoleService#hasRole(User, Namespace, IRole)
   */
  public boolean hasRole(String username, String namespaceName, String roleName) {
    LOGGER.info(String
        .format("Retrieving user, namespace [%s] and role [%s]", namespaceName,
            roleName));
    User user = userRepository.findByUsername(username);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    IRole role = namespaceRoleRepository.find(roleName);
    return hasRole(user, namespace, role);
  }

  /**
   * Returns all {@link IRole}s the given {@link User} has on the given {@link Namespace}.
   *
   * @param user
   * @param namespace
   * @return all {@link IRole}s the given {@link User} has on the given {@link Namespace}.
   */
  public Collection<IRole> getRoles(User user, Namespace namespace) {
    validator.validateNulls(user, namespace);
    LOGGER.info(String
        .format("Retrieving roles for user and namespace [%s]", namespace.getName()));
    UserNamespaceRoles userNamespaceRoles = userNamespaceRoleRepository
        .findOne(new UserNamespaceID(user, namespace));
    return roleUtil.toNamespaceRoles(userNamespaceRoles.getRoles());
  }

  /**
   * @param username
   * @param namespaceName
   * @return
   * @see UserNamespaceRoleService#getRoles(User, Namespace)
   */
  public Collection<IRole> getRoles(String username, String namespaceName) {
    User user = userRepository.findByUsername(username);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    return getRoles(user, namespace);
  }

  /**
   * Adds the given {@link IRole} to the given {@link User} on the given {@link Namespace}, as
   * acted by the {@literal actor} {@link User} if so authorized.<br/>
   * The pre-condition for authorizing this operation is that the actor is either sysadmin, or has
   * administrative privileges on the given {@link Namespace}.
   *
   * @param actor
   * @param target
   * @param namespace
   * @param role
   * @return {@literal true} if the user did not have the role on the namespace prior to adding it, {@literal false} if they already had the role.
   * @see org.eclipse.vorto.repository.account.impl.DefaultUserAccountService#addUserToTenant(String, String, Role...)
   */
  public boolean addRole(User actor, User target, Namespace namespace, IRole role)
      throws OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace, role);
    validator.validateNulls(actor.getId(), target.getId());

    // authorizing actor
    authorizeActorAsAdminOnNamespace(actor, namespace);

    UserNamespaceRoles roles = userNamespaceRoleRepository
        .findOne(new UserNamespaceID(target, namespace));
    // no association exists yet between given user and namespace
    if (roles == null) {
      roles = new UserNamespaceRoles();
      roles.setID(new UserNamespaceID(target, namespace));
      roles.setRoles(roles.getRoles() + role.getRole());
      return userNamespaceRoleRepository.save(roles) != null;
    } else {
      // user already has that role on that namespace
      if ((roles.getRoles() & role.getRole()) == role.getRole()) {
        return false;
      } else {
        roles.setRoles(roles.getRoles() + role.getRole());
        return userNamespaceRoleRepository.save(roles) != null;
      }
    }
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param namespaceName
   * @param roleName
   * @return
   * @see UserNamespaceRoleService#addRole(User, User, Namespace, IRole)
   */
  public boolean addRole(String actorUsername, String targetUsername, String namespaceName,
      String roleName) throws OperationForbiddenException {
    LOGGER.info(String
        .format("Retrieving user, namespace [%s] and role [%s]", namespaceName,
            roleName));
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    IRole role = namespaceRoleRepository.find(roleName);
    return addRole(actor, target, namespace, role);
  }

  /**
   * Removes the given {@link IRole} from the given {@link User} on the given {@link Namespace}.<br/>
   * Will return {@literal true} if the user and namespace association exists and the user had that
   * role on the namespace.<br/>
   * Will return {@literal false} for any other reason, such as: no user-namespace association
   * exists, the user did not have that role on the namespace to start with, other failures at
   * repository-level...
   *
   * @param actor
   * @param target
   * @param namespace
   * @param role
   * @return {@literal true} if the operation succeeded, {@literal false} otherwise or if not applicable.
   */
  public boolean removeRole(User actor, User target, Namespace namespace, IRole role)
      throws OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace, role);
    validator.validateNulls(actor.getId(), target.getId());

    // authorizing actor on namespace
    authorizeActorAsAdminOnNamespace(actor, namespace);

    UserNamespaceRoles roles = userNamespaceRoleRepository
        .findOne(new UserNamespaceID(target, namespace));
    // no association exists between given user and namespace
    if (roles == null) {
      return false;
    } else {
      // user does not have that role on that namespace
      if ((roles.getRoles() & role.getRole()) != role.getRole()) {
        return false;
      } else {
        roles.setRoles(roles.getRoles() - role.getRole());
        return userNamespaceRoleRepository.save(roles) != null;
      }
    }
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param namespaceName
   * @param roleName
   * @return
   * @see UserNamespaceRoleService#removeRole(User, User, Namespace, IRole)
   */
  public boolean removeRole(String actorUsername, String targetUsername, String namespaceName,
      String roleName) throws OperationForbiddenException {
    LOGGER.info(String
        .format("Retrieving user, namespace [%s] and role [%s]", namespaceName,
            roleName));
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    IRole role = namespaceRoleRepository.find(roleName);
    return removeRole(actor, target, namespace, role);
  }

  /**
   * Sets the roles of the given {@link User} on the given {@link Namespace} with a value as a power
   * of {@literal 2}.<br/>
   * This method is private as the numeric value is not checked.
   *
   * @param actor
   * @param target
   * @param namespace
   * @param rolesValue
   * @return {@literal true} if operation succeeded, {@literal false} if operation not required or failed to persist.
   * @see UserNamespaceRoleService#setRoles(User, User, Namespace, Collection)
   */
  private boolean setRoles(User actor, User target, Namespace namespace, long rolesValue)
      throws OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace);
    validator.validateNulls(actor.getId(), target.getId());

    // authorizing actor on namespace
    authorizeActorAsAdminOnNamespace(actor, namespace);

    // retrieving existing roles
    UserNamespaceRoles roles = userNamespaceRoleRepository
        .findOne(new UserNamespaceID(target, namespace));

    // no association exists yet between given user and namespace
    if (roles == null) {
      roles = new UserNamespaceRoles();
      roles.setRoles(rolesValue);
      roles.setUser(target);
      roles.setNamespace(namespace);
      return userNamespaceRoleRepository.save(roles) != null;
    } else {
      // user already has those roles on that namespace
      if (roles.getRoles() == rolesValue) {
        return false;
      } else {
        roles.setRoles(rolesValue);
        return userNamespaceRoleRepository.save(roles) != null;
      }
    }
  }

  /**
   * Assigns the given {@link IRole}s to the given {@link User} on the given {@link Namespace}.<br/>
   * Overwrites existing roles if any.
   *
   * @param target
   * @param actor
   * @param namespace
   * @param roles
   * @return {@literal true} if operation succeeded, {@literal false} if operation not required or failed to persist.
   * @see UserNamespaceRoleService#addRole(User, User, Namespace, IRole) to add a new role while preserving existing ones.
   */
  public boolean setRoles(User actor, User target, Namespace namespace, Collection<IRole> roles)
      throws DoesNotExistException, OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace, roles);
    validator.validateNulls(actor.getId(), target.getId(),
        roles.stream().map(IRole::getName).collect(Collectors.toSet()));

    for (IRole role : roles) {
      if (namespaceRoleRepository.find(role.getName()) == null) {
        throw new DoesNotExistException("Unknown role - aborting operation.");
      }
    }
    return setRoles(actor, target, namespace,
        roles.stream().collect(Collectors.summingLong(IRole::getRole)));
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param namespaceName
   * @param roleNames
   * @return
   * @see UserNamespaceRoleService#setRoles(User, User, Namespace, Collection)
   */
  public boolean setRoles(String actorUsername, String targetUsername, String namespaceName,
      Collection<String> roleNames) throws DoesNotExistException, OperationForbiddenException {
    LOGGER.info(String
        .format("Retrieving user, namespace [%s] and roles [%s]", namespaceName,
            roleNames));
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    Collection<IRole> roles = roleUtil.toNamespaceRoles(roleNames);
    return setRoles(actor, target, namespace, roles);
  }

  /**
   * Sets all available roles to the given {@link User} on the given {@link Namespace}.<br/>
   * Does not impact on namespace ownership.
   *
   * @param actor
   * @param target
   * @param namespace
   * @return
   */
  public boolean setAllRoles(User actor, User target, Namespace namespace)
      throws DoesNotExistException, OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace);
    return setRoles(actor, target, namespace,
        namespaceRoleRepository.findAll().stream().map(r -> (IRole) r).collect(
            Collectors.toSet()));
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param namespaceName
   * @return
   * @see UserNamespaceRoleService#setAllRoles(User, User, Namespace)
   */
  public boolean setAllRoles(String actorUsername, String targetUsername, String namespaceName)
      throws DoesNotExistException, OperationForbiddenException {
    LOGGER.info(String
        .format("Retrieving user and namespace [%s]", namespaceName));
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    return setAllRoles(actor, target, namespace);
  }

  /**
   * Deletes the {@link User} + {@link Namespace} role association for the given {@link User} and
   * {@link Namespace} entirely. <br/>
   * If the given {@link User} owns the {@link Namespace}, logs a warning to signify the namespace
   * ownership will be "orphaned", and the namespace should either be deleted or its ownership
   * transferred to another user.<br/>
   * The operation is permitted only if the {@literal actor} {@link User} is either sysadmin, or
   * the owner of the given {@link Namespace}.
   *
   * @param actor
   * @param target
   * @param namespace
   * @return
   * @throws DoesNotExistException
   * @see org.eclipse.vorto.repository.account.impl.DefaultUserAccountService#removeUserFromTenant(String, String)
   */
  @Transactional(rollbackOn = {DoesNotExistException.class, OperationForbiddenException.class})
  public boolean deleteAllRoles(User actor, User target, Namespace namespace)
      throws DoesNotExistException, OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, target, namespace);
    validator.validateNulls(actor.getId(), target.getId(), namespace.getId());

    // namespace does not exist
    if (!namespaceRepository.exists(namespace.getId())) {
      throw new DoesNotExistException(
          "Namespace [%s] does not exist - aborting deletion of user roles.");
    }

    // checking users
    if (!userRepository.exists(actor.getId())) {
      throw new DoesNotExistException("Acting user does not exist - aborting deletion of roles.");
    }
    if (!userRepository.exists(target.getId())) {
      throw new DoesNotExistException("Target user does not exist - aborting deletion of roles.");
    }

    // checking actor privileges

    // actor not sysadmin
    if (!userRepositoryRoleService.isSysadmin(actor)) {
      // actor not target, or target does not own the namespace
      if (!actor.equals(target) || !target.equals(namespace.getOwner())) {
        throw new OperationForbiddenException(
            String.format("Acting user cannot delete user roles for namespace [%s].",
                namespace.getName())
        );
      }
    }

    UserNamespaceRoles rolesToDelete = new UserNamespaceRoles();
    rolesToDelete.setUser(target);
    rolesToDelete.setNamespace(namespace);

    // user-namespace role association does not exist
    if (!userNamespaceRoleRepository.exists(rolesToDelete.getID())) {
      LOGGER.warn("Attempting to delete non existing user namespace roles. Aborting.");
      return false;
    }

    userNamespaceRoleRepository.delete(rolesToDelete.getID());
    LOGGER.info("Deleted user-namespace role association.");

    return true;
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param namespaceName
   * @return
   * @throws DoesNotExistException
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#deleteAllRoles(User, User, Namespace)
   */
  public boolean deleteAllRoles(String actorUsername, String targetUsername, String namespaceName)
      throws DoesNotExistException, OperationForbiddenException {
    LOGGER.info(String.format("Retrieving users and namespace [%s].", namespaceName));
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    return deleteAllRoles(actor, target, namespace);
  }

  /**
   * Verifies whether the given {@link User} can view the given {@link Namespace}, which always
   * yields true if the user is {@literal sysadmin}.<br/>
   *
   * @param user
   * @param namespace
   * @throws OperationForbiddenException if the user has no view privilege on the namespace.
   */
  public void verifyCanView(User user, Namespace namespace) throws OperationForbiddenException {
    if (!userRepositoryRoleService.isSysadmin(user)) {
      UserNamespaceRoles actorRoles = userNamespaceRoleRepository
          .findOne(new UserNamespaceID(user, namespace));
      // acting user has no visibility on namespace
      if (!namespace.getOwner().equals(user) || actorRoles == null) {
        throw new OperationForbiddenException(
            String.format("User has no visibility on namespace [%s].", namespace.getName())
        );
      }
    }
  }

  /**
   * Retrieves the {@link User}s who collaborate (i.e. have any {@link IRole}) on the given
   * {@link Namespace}, filtered optionally by the given roles, expressed as {@code long}.<br/>
   * The operation fails if the acting {@link User} is not authorited to perform it, i.e. they are
   * neither {@literal sysadmin}, nor have any read privilege on the givne {@link Namespace}.
   *
   * @param actor
   * @param namespace
   * @param roles
   * @return
   * @throws OperationForbiddenException
   */
  public Collection<User> getUsers(User actor, Namespace namespace, Long roles)
      throws OperationForbiddenException {
    // boilerplate null validation
    validator.validateNulls(actor, namespace);

    // authorize actor
    verifyCanView(actor, namespace);

    if (roles == null) {
      return userNamespaceRoleRepository.findAllByNamespace(namespace).stream()
          .map(UserNamespaceRoles::getUser).collect(Collectors.toSet());
    } else {
      return userNamespaceRoleRepository.findAllByNamespaceAndRoles(namespace, roles).stream()
          .map(UserNamespaceRoles::getUser).collect(Collectors.toSet());
    }
  }

  /**
   * @param actor
   * @param namespace
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getUsers(User, Namespace, Long)
   */
  public Collection<User> getUsers(User actor, Namespace namespace)
      throws OperationForbiddenException {
    return getUsers(actor, namespace, null);

  }

  /**
   * @param actorUsername
   * @param namespaceName
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getUsers(User, Namespace)
   */
  public Collection<User> getUsers(String actorUsername, String namespaceName)
      throws OperationForbiddenException {
    User actor = userRepository.findByUsername(actorUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    return getUsers(actor, namespace);
  }

  /**
   * Builds a {@link Map} of {@link User}s with their respective {@link IRole}s in the given
   * {@link Namespace}, as performed by the acting {@link User}.<br/>
   * The operation will fail if the acting {@link User} is not authorized, i.e. if they are neither
   * {@literal sysadmin} or have no view privileges on the given {@link Namespace}.
   *
   * @param actor
   * @param namespace
   * @return
   * @throws OperationForbiddenException
   */
  public Map<User, Collection<IRole>> getRolesByUser(User actor, Namespace namespace)
      throws OperationForbiddenException {

    validator.validateNulls(actor, namespace);

    authorizeActorAsAdminOnNamespace(actor, namespace);

    TreeMap<User, Collection<IRole>> result = new TreeMap<>(
        Comparator.comparing(User::getUsername));
    userNamespaceRoleRepository.findAllByNamespace(namespace).stream()
        .forEach(
            unr -> {
              result.put(unr.getUser(), roleUtil.toNamespaceRoles(unr.getRoles()));
            }
        );
    return result;
  }

  /**
   * @param actorUsername
   * @param namespaceName
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getRolesByUser(User, Namespace)
   */
  public Map<User, Collection<IRole>> getRolesByUser(String actorUsername,
      String namespaceName) throws OperationForbiddenException {
    User actor = userRepository.findByUsername(actorUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    return getRolesByUser(actor, namespace);
  }

  /**
   * This functionality is slightly out of scope in every service. <br/>
   * Its purpose is to create an ad-hoc "technical" user, and add it as collaborator to a given
   * {@link Namespace} with the desired roles.<br/>
   * Presently, there is no scenario where a technical user can be created without being also
   * added as collaborator to a namespace, hence the mixed operation here.
   *
   * @param actor
   * @param technicalUser
   * @param namespace
   * @param roles
   * @see org.eclipse.vorto.repository.account.impl.DefaultUserAccountService#createTechnicalUserAndAddToTenant(String, String, ICollaborator, Role...)
   */
  @Transactional(rollbackOn = {OperationForbiddenException.class, InvalidUserException.class})
  public void createTechnicalUserAndAddAsCollaborator(User actor, User technicalUser,
      Namespace namespace, long roles) throws OperationForbiddenException, InvalidUserException {
    // boilerplate null validation
    validator.validateNulls(actor, technicalUser, namespace);

    // delegates tech user creation and persistence to user service
    userService.createOrUpdateTechnicalUser(technicalUser);

    // sets the desired namespace-roles association - authorization on namespace is
    // performed here, but failure will revert the transaction including saving the new user
    setRoles(actor, technicalUser, namespace, roles);
  }

  /**
   * @param actor
   * @param technicalUser
   * @param namespace
   * @param roles
   * @throws OperationForbiddenException
   * @throws InvalidUserException
   * @see UserNamespaceRoleService#createTechnicalUserAndAddAsCollaborator(User, User, Namespace, long)
   */
  public void createTechnicalUserAndAddAsCollaborator(User actor, User technicalUser,
      Namespace namespace, Collection<IRole> roles)
      throws OperationForbiddenException, InvalidUserException {
    createTechnicalUserAndAddAsCollaborator(actor, technicalUser, namespace,
        roleUtil.toLong(roles));
  }

  /**
   * @param actorUsername
   * @param technicalUser
   * @param namespaceName
   * @param roles
   * @throws OperationForbiddenException
   * @throws InvalidUserException
   * @see UserNamespaceRoleService#createTechnicalUserAndAddAsCollaborator(User, User, Namespace, long)
   */
  public void createTechnicalUserAndAddAsCollaborator(String actorUsername, User technicalUser,
      String namespaceName, Collection<String> roles)
      throws OperationForbiddenException, InvalidUserException {
    User actor = userRepository.findByUsername(actorUsername);
    Namespace namespace = namespaceRepository.findByName(namespaceName);
    Collection<IRole> actualRoles = roleUtil
        .toNamespaceRoles(roles.toArray(new String[roles.size()]));
    createTechnicalUserAndAddAsCollaborator(actor, technicalUser, namespace, actualRoles);
  }

  /**
   * Retrieves all {@link Namespace}s where the given target {@link User} is a collaborator, as
   * acted by the given acting {@link User}.<br/>
   * The {@code roleFilter} parameter can be {@code null} - in which case all {@link Namespace}s
   * where the target {@link User} has any role are returned; otherwise only the {@link Namespace}s
   * where the target {@link User} has the roles represented by the filter are returned.<br/>
   * The operation can fail if the acting {@link User} is neither {@literal sysadmin} nor the same
   * {@link User} as the target.
   *
   * @param actor
   * @param target
   * @param roleFilter
   * @return
   * @throws OperationForbiddenException
   */
  public Collection<Namespace> getNamespaces(User actor, User target, Long roleFilter)
      throws OperationForbiddenException {
    // boilerplate null validation - role filter can be null
    validator.validateNulls(actor, target);

    // authorizes actor
    userUtil.authorizeActorAsTargetOrSysadmin(actor, target);

    // if target user is sysadmin, then retrieves all namespaces optionally filtered by role
    if (userRepositoryRoleService.isSysadmin(target)) {
      return userNamespaceRoleRepository.findAll().stream()
          .filter(roleFilter != null ? unr -> ((unr.getRoles() & roleFilter) == roleFilter)
              : unr -> true)
          .map(UserNamespaceRoles::getNamespace).collect(
              Collectors.toSet());
    }

    // retrieves namespaces either filtered by role(s) or all namespaces where target has a role
    if (roleFilter == null) {
      return userNamespaceRoleRepository.findAllByUser(target).stream()
          .map(UserNamespaceRoles::getNamespace).collect(
              Collectors.toSet());
    } else {
      return userNamespaceRoleRepository.findAllByUserAndRoles(target, roleFilter).stream()
          .map(UserNamespaceRoles::getNamespace).collect(Collectors.toSet());
    }
  }

  /**
   * Retrieves all {@link Namespace}s where the given target {@link User} has any role, as acted by
   * the given actor {@link User}.
   *
   * @param actor
   * @param target
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespaces(User, User, Long)
   */
  public Collection<Namespace> getNamespaces(User actor, User target)
      throws OperationForbiddenException {
    return getNamespaces(actor, target, (Long) null);
  }

  /**
   * @param actor
   * @param target
   * @param roles
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespaces(User, User, Long)
   */
  public Collection<Namespace> getNamespaces(User actor, User target, IRole... roles)
      throws OperationForbiddenException {
    return getNamespaces(actor, target,
        roles != null ? roleUtil.toLong(Arrays.asList(roles)) : null);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param roles
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespaces(User, User, Long)
   */
  public Collection<Namespace> getNamespaces(String actorUsername, String targetUsername,
      IRole... roles) throws OperationForbiddenException {
    validator.validateNulls(actorUsername, targetUsername);
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Long actualRoles = null;
    if (roles != null && roles.length > 0) {
      actualRoles = roleUtil.toLong(Arrays.asList(roles));
    }
    return getNamespaces(actor, target, actualRoles);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param roles
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespaces(User, User, Long)
   */
  public Collection<Namespace> getNamespaces(String actorUsername, String targetUsername,
      String... roles) throws OperationForbiddenException {
    return getNamespaces(actorUsername, targetUsername,
        roles != null ? Arrays.stream(roles).map(namespaceRoleRepository::find)
            .toArray(IRole[]::new) : null);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespaces(User, User, Long)
   */
  public Collection<Namespace> getNamespaces(String actorUsername, String targetUsername)
      throws OperationForbiddenException {
    return getNamespaces(actorUsername, targetUsername, (IRole[]) null);
  }

  /**
   * Returns all {@link Namespace}s where the target {@link User} has the given roles, mapping by
   * {@link Namespace}, then by collaborators ({@link User}s) and {@link IRole}s, as acted by the
   * given actor {@link User}.<br/>
   * The operation can fail to authorize if the acting {@link User} neither has the
   * {@literal sysadmin} repository role, and they are not the target {@link User}.<br/>
   * If no role filter is specified, will return the map of all {@link Namespace}s where the
   * target {@link User} has any role.
   *
   * @param actor
   * @param target
   * @param roleFilter
   * @return
   */
  public Map<Namespace, Map<User, Collection<IRole>>> getNamespacesCollaboratorsAndRoles(User actor,
      User target, Long roleFilter) throws OperationForbiddenException {
    // boilerplate null validation - role filter can be null
    validator.validateNulls(actor, target);

    // authorizes actor
    userUtil.authorizeActorAsTargetOrSysadmin(actor, target);

    Map<Namespace, Map<User, Collection<IRole>>> result = new HashMap<>();

    Collection<Namespace> namespaces = getNamespaces(actor, target, roleFilter);
    for (Namespace n : namespaces) {
      result.putIfAbsent(n, getRolesByUser(actor, n));
    }
    return result;
  }

  /**
   * @param actor
   * @param target
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespacesCollaboratorsAndRoles(User, User, Long)
   */
  public Map<Namespace, Map<User, Collection<IRole>>> getNamespacesCollaboratorsAndRoles(User actor,
      User target) throws OperationForbiddenException {
    return getNamespacesCollaboratorsAndRoles(actor, target, null);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param roles
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespacesCollaboratorsAndRoles(User, User, Long)
   */
  public Map<Namespace, Map<User, Collection<IRole>>> getNamespacesCollaboratorsAndRoles(
      String actorUsername, String targetUsername, IRole... roles)
      throws OperationForbiddenException {
    // boilerplate null validation - role filter can be null
    validator.validateNulls(actorUsername, targetUsername);

    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    Long actualRoles = null;
    if (roles != null && roles.length > 0) {
      actualRoles = roleUtil.toLong(Arrays.asList(roles));
    }

    return getNamespacesCollaboratorsAndRoles(actor, target, actualRoles);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @param roles
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespacesCollaboratorsAndRoles(User, User, Long)
   */
  public Map<Namespace, Map<User, Collection<IRole>>> getNamespacesCollaboratorsAndRoles(
      String actorUsername, String targetUsername, String... roles)
      throws OperationForbiddenException {
    return getNamespacesCollaboratorsAndRoles(actorUsername, targetUsername,
        roles != null ? Arrays.stream(roles).map(namespaceRoleRepository::find)
            .toArray(IRole[]::new) : null);
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#getNamespacesCollaboratorsAndRoles(User, User, Long)
   */
  public Map<Namespace, Map<User, Collection<IRole>>> getNamespacesCollaboratorsAndRoles(
      String actorUsername, String targetUsername) throws OperationForbiddenException {
    return getNamespacesCollaboratorsAndRoles(actorUsername, targetUsername, (IRole[]) null);
  }

  /**
   * Verifies whether the target {@link User} is the only user yielding the namespace_admin role
   * in any namespace this user is associated to. <br/>
   * The operation can fail if the acting {@link User} is not authorized to perform the operation.
   *
   * @param actor
   * @param target
   * @return
   * @throws OperationForbiddenException
   */
  public boolean isOnlyAdminInAnyNamespace(User actor, User target)
      throws OperationForbiddenException {
    // authorizing actor
    userUtil.authorizeActorAsTargetOrSysadmin(actor, target);

    // retrieving namespaces where user is namespace_admin, alongside users and roles
    Map<Namespace, Map<User, Collection<IRole>>> namespacesWhereTargetIsAdmin = getNamespacesCollaboratorsAndRoles(
        actor, target,
        roleUtil.toLong(namespaceAdminRole()));

    // crawling namespaces and inferring whether target user is only admin for any
    for (Map.Entry<Namespace, Map<User, Collection<IRole>>> userRoles : namespacesWhereTargetIsAdmin
        .entrySet()) {
      if (userRoles.getValue().size() == 1) {
        LOGGER.debug(
            String.format(
                "User is the only administrator of at least one namespace : [%s].",
                userRoles.getKey().getName()
            )
        );
        return true;
      }
    }
    return false;
  }

  /**
   * @param actorUsername
   * @param targetUsername
   * @return
   * @throws OperationForbiddenException
   * @see UserNamespaceRoleService#isOnlyAdminInAnyNamespace(User, User)
   */
  public boolean isOnlyAdminInAnyNamespace(String actorUsername, String targetUsername)
      throws OperationForbiddenException {
    validator.validateNulls(actorUsername, targetUsername);
    User actor = userRepository.findByUsername(actorUsername);
    User target = userRepository.findByUsername(targetUsername);
    return isOnlyAdminInAnyNamespace(actor, target);
  }


}
