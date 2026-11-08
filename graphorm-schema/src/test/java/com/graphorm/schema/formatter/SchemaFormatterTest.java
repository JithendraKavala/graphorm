package com.graphorm.schema.formatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFormatterTest {

    @Test
    @DisplayName("SchemaFormatter formats unaligned schema into canonical aligned columns")
    void formatsUnalignedSchema() {
        String unformatted = """
                // Global header comment
                datasource db {
                provider = "postgresql"
                url = env("DATABASE_URL")
                }
                
                generator client {
                provider = "graphorm-client-java"
                }
                
                model User {
                id String @id @default(uuid())
                email String @unique
                age Int?
                bio String? @db.VarChar(255)
                createdAt DateTime @default(now())
                }
                """;

        String formatted = SchemaFormatter.format(unformatted);

        assertThat(formatted).contains("// Global header comment");
        assertThat(formatted).contains("datasource db {");
        assertThat(formatted).contains("  provider = \"postgresql\"");
        assertThat(formatted).contains("  url      = env(\"DATABASE_URL\")");
        assertThat(formatted).contains("model User {");
        assertThat(formatted).contains("  id         String    @id @default(uuid())");
        assertThat(formatted).contains("  email      String    @unique");
        assertThat(formatted).contains("  age        Int?");
        assertThat(formatted).contains("  bio        String?   @db.VarChar(255)");
        assertThat(formatted).contains("  createdAt  DateTime  @default(now())");
    }

    @Test
    @DisplayName("SchemaFormatter is strictly idempotent: format(format(source)) == format(source)")
    void formattingIsIdempotent() {
        String schema = """
                // Header comment
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                enum Role {
                  ADMIN
                  USER
                  GUEST
                }

                model User {
                  id      String  @id @default(uuid())
                  name    String
                  email   String  @unique
                  role    Role    @default(USER)
                  version Int     @version @default(1)
                }

                view ActiveUserSummary {
                  id    String @id
                  name  String
                  email String
                  @@view
                }
                """;

        String pass1 = SchemaFormatter.format(schema);
        String pass2 = SchemaFormatter.format(pass1);

        assertThat(pass2).isEqualTo(pass1);
    }

    @Test
    @DisplayName("SchemaFormatter preserves comments across models and fields")
    void preservesComments() {
        String schema = """
                // Top level doc
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                // Important User model
                model User {
                  // Primary identifier
                  id    String @id
                  // User's contact email
                  email String @unique
                }
                """;

        String formatted = SchemaFormatter.format(schema);
        assertThat(formatted).contains("// Top level doc");
        assertThat(formatted).contains("// Important User model");
        assertThat(formatted).contains("// Primary identifier");
        assertThat(formatted).contains("// User's contact email");
    }
}
