package io.realworld.users

import io.realworld.FieldError
import io.realworld.UnauthrorizedException
import io.realworld.domain.common.Auth
import io.realworld.domain.users.GetUser
import io.realworld.domain.users.GetUserByEmail
import io.realworld.domain.users.LoginUserCommand
import io.realworld.domain.users.LoginUserUseCase
import io.realworld.domain.users.RegisterUserCommand
import io.realworld.domain.users.RegisterUserUseCase
import io.realworld.domain.users.SaveUser
import io.realworld.domain.users.SaveUserIO
import io.realworld.domain.users.User
import io.realworld.domain.users.UserRegistration
import io.realworld.domain.users.UserRegistrationValidationError
import io.realworld.domain.users.UserRepository
import io.realworld.domain.users.ValidateUser
import io.realworld.domain.users.ValidateUserRegistration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

data class UserResponse(val user: UserResponseDto) {
  companion object {
    fun fromDomain(domain: User) = UserResponse(UserResponseDto.fromDomain(domain))
  }
}

@RestController
class UserController(
  private val auth0: Auth,
  private val userRepository0: UserRepository
) {

  @GetMapping("/api/user")
  fun currentUser(user: User) = ResponseEntity.ok().body(UserResponse.fromDomain(user))

  @PostMapping("/api/users")
  fun register(@Valid @RequestBody registration: RegistrationDto): ResponseEntity<UserResponse> {
    val validateUser = object : ValidateUser { override val userRepository = userRepository0 }
    val saveUser = object : SaveUserIO { override val userRepository = userRepository0 }

    return object: RegisterUserUseCase {
      override val auth = auth0
      override val saveUser: SaveUser = { x -> saveUser.run { x.save() } }
      override val validateUser: ValidateUserRegistration = { x -> validateUser.run { x.validate() } }
    }.run {
      RegisterUserCommand(UserRegistration(
        username = registration.username,
        email = registration.email,
        password = registration.password
      )).registerUser()
    }
      .unsafeRunSync()
      .fold(
        {
          when (it) {
            is UserRegistrationValidationError.EmailAlreadyTaken ->
              throw FieldError("email", "already taken")
            is UserRegistrationValidationError.UsernameAlreadyTaken ->
              throw FieldError("username", "already taken")
          }
        },
        { ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromDomain(it)) }
      )
  }

  @PostMapping("/api/users/login")
  fun login(@Valid @RequestBody login: LoginDto): ResponseEntity<UserResponse> {
    val getUserByEmail = object : GetUserByEmail {
      override val userRepository = userRepository0
    }

    return object : LoginUserUseCase {
      override val auth = auth0
      override val getUser: GetUser = { x -> getUserByEmail.run { x.getUser() } }
    }.run {
      LoginUserCommand(
        email = login.email,
        password = login.password
      ).loginUser()
    }
      .unsafeRunSync()
      .fold(
        { throw UnauthrorizedException() },
        { ResponseEntity.ok().body(UserResponse.fromDomain(it)) })
  }

  @PutMapping("/api/user")
  fun update(@Valid @RequestBody userUpdate: UserUpdateDto, user: User): ResponseEntity<UserResponse> =
    ResponseEntity.ok().body(UserResponse.fromDomain(user))
}
