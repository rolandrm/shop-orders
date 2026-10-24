package errorfixture;

import com.shop.orders.platform.web.Results;
import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contrôleur de test : produit chaque type d'erreur. Chargé uniquement par {@code @Import}. */
@RestController
public class ErrorFixtureController {

  public record Body(@NotBlank String name) {}

  @GetMapping("/fixture/not-found")
  String notFound() {
    return Results.unwrap(failure(DomainError.Kind.NOT_FOUND, "ORDER_NOT_FOUND"));
  }

  @GetMapping("/fixture/validation")
  String validation() {
    return Results.unwrap(failure(DomainError.Kind.VALIDATION, "ORDER_INVALID"));
  }

  @GetMapping("/fixture/conflict")
  String conflict() {
    return Results.unwrap(failure(DomainError.Kind.CONFLICT, "ORDER_CONFLICT"));
  }

  @GetMapping("/fixture/unavailable")
  String unavailable() {
    return Results.unwrap(failure(DomainError.Kind.UNAVAILABLE, "CATALOG_UNAVAILABLE"));
  }

  @GetMapping("/fixture/boom")
  String boom() {
    throw new IllegalStateException("secret interne");
  }

  @PostMapping("/fixture/body")
  String body(@Valid @RequestBody Body body) {
    return body.name();
  }

  @GetMapping("/fixture/header")
  String header(@RequestHeader("X-Customer-Id") UUID customerId) {
    return customerId.toString();
  }

  @GetMapping("/fixture/uuid")
  String uuid(@RequestParam("id") UUID id) {
    return id.toString();
  }

  // Pas de @Validated sur la classe : la validation de méthode intégrée à Spring MVC lève alors
  // HandlerMethodValidationException (avec @Validated, l'AOP lèverait
  // ConstraintViolationException).
  @GetMapping("/fixture/bounded")
  String bounded(@RequestParam("limit") @Min(1) @Max(100) int limit) {
    return Integer.toString(limit);
  }

  private static Result<String> failure(DomainError.Kind kind, String code) {
    return new Result.Failure<>(new DomainError(kind, code, "message " + code));
  }
}
