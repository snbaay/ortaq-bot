ALTER TABLE expense ADD COLUMN IF NOT EXISTS linked_workflow VARCHAR(16);

CREATE TABLE purchase_shopping_item (
    purchase_id BIGINT NOT NULL REFERENCES purchase(id),
    shopping_item_id BIGINT NOT NULL REFERENCES shopping_item(id),
    PRIMARY KEY (purchase_id, shopping_item_id),
    UNIQUE (shopping_item_id)
);
