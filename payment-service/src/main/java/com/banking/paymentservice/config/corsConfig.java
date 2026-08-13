package com.banking.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class corsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(){

        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
               registry.addMapping("/api/**")
                       .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                       .allowedHeaders("*") // Allow all headers (Authorization, Content-Type, etc.)
                       .exposedHeaders("Authorization") // Headers accessible to the frontend client
                       .allowCredentials(true); // Set to true if you are sending cookies/sessions
            }
        };
    }
}


/**
 * Method 2: Controller-Level Configuration
 * If you only need to allow cross-origin access on one specific controller or individual endpoint,
 * use the @CrossOrigin annotation
 * ------------------------------------
 * On the Entire Controller:
 * @RestController
 * @RequestMapping("/api/products")
 * @CrossOrigin(origins = "http://localhost:3000") // Applies to all methods in this class
 * public class ProductController {
 *
 *     @GetMapping
 *     public List<Product> getAll() {
 *         return productService.findAll();
 *     }
 * }
 * -----------------------------------------
 * On a Specific Method:
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController {
 *
 *     @CrossOrigin(origins = "http://localhost:3000") // Only this method allows CORS
 *     @GetMapping("/{id}")
 *     public ResponseEntity<User> getUser(@PathVariable Long id) {
 *         return ResponseEntity.ok(userService.findById(id));
 *     }
 * }
 *
 *
 */

//----------------------------------------------------------------------
//----------------------------------------------------------------------
/// Method 3: For Spring Security (If you use it)
/**
 * If you have Spring Security in your project,
 * global CORS configuration via WebMvcConfigurer might get blocked by security filters.
 * You must configure CORS explicitly inside your Security filter chain bean.java
 * -----------------------------------------------------------
 * import org.springframework.context.annotation.Bean;
 * import org.springframework.context.annotation.Configuration;
 * import org.springframework.security.config.annotation.web.builders.HttpSecurity;
 * import org.springframework.security.web.SecurityFilterChain;
 * import org.springframework.web.cors.CorsConfiguration;
 * import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
 * import java.util.Arrays;
 * import java.util.List;
 *
 * @Configuration
 * public class SecurityConfig {
 *
 *     @Bean
 *     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
 *         http
 *             .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Register CORS config
 *             .csrf(csrf -> csrf.disable()) // Only disable if using tokens (JWT)
 *             .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
 *
 *         return http.build();
 *     }
 *
 *     @Bean
 *     public UrlBasedCorsConfigurationSource corsConfigurationSource() {
 *         CorsConfiguration configuration = new CorsConfiguration();
 *         configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
 *         configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
 *         configuration.setAllowedHeaders(Arrays.asList("*"));
 *         configuration.setAllowCredentials(true);
 *
 *         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
 *         source.registerCorsConfiguration("/**", configuration);
 *         return source;
 *     }
 * }
 */