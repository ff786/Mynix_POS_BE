-- Website details for products.
--   name             short name used on POS screens (unchanged)
--   full_name        full product name: shown on the website and printed on invoices
--   description      detailed description for the product page
--   show_on_website  staff choose which products the website lists
--   slug             the product page address: mynix.lk/products/<slug>
--   seo_*            per-product search title, description and keywords
--   image_alt        text describing the product photo (search + screen readers)
ALTER TABLE products
    ADD COLUMN full_name       VARCHAR(255),
    ADD COLUMN description     TEXT,
    ADD COLUMN show_on_website BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN slug            VARCHAR(120),
    ADD COLUMN seo_title       VARCHAR(70),
    ADD COLUMN seo_description VARCHAR(170),
    ADD COLUMN seo_keywords    VARCHAR(255),
    ADD COLUMN image_alt       VARCHAR(160);

-- Existing products start with their full name equal to the current name.
UPDATE products SET full_name = name;
ALTER TABLE products ALTER COLUMN full_name SET NOT NULL;

-- Page address from the name ("10x Magnifying Loupe" -> "10x-magnifying-loupe").
-- When two products share a name, the active (then oldest) one keeps the plain
-- address and the others get their id appended (ids are unique, so this can't
-- collide again).
WITH base AS (
    SELECT id, active,
           COALESCE(NULLIF(LEFT(TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(name), '[^a-z0-9]+', '-', 'g')), 100), ''), 'product') AS slug
    FROM products
), numbered AS (
    SELECT id, slug, ROW_NUMBER() OVER (PARTITION BY slug ORDER BY active DESC, id) AS n
    FROM base
)
UPDATE products p
SET slug = CASE WHEN numbered.n = 1 THEN numbered.slug ELSE numbered.slug || '-' || numbered.id END
FROM numbered
WHERE numbered.id = p.id;

ALTER TABLE products ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX ux_products_slug ON products (slug);
