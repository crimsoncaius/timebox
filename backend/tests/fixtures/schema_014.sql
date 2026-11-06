-- Frozen SQLite schema from repository models at commit 436e2649 (revision 014).
-- Exported via Base.metadata.create_all and sqlite_master, including constraints/triggers.
-- Never regenerate from current models: later migrations must encounter a historical schema.

CREATE TABLE actual_block_record_operations (
	token TEXT NOT NULL,
	actual_block_id INTEGER,
	planned_block_id INTEGER,
	invalidated_at DATETIME,
	undone_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (token),
	FOREIGN KEY(actual_block_id) REFERENCES time_blocks (id) ON DELETE SET NULL,
	FOREIGN KEY(planned_block_id) REFERENCES time_blocks (id) ON DELETE SET NULL
);
CREATE TABLE app_settings (
	id INTEGER NOT NULL,
	start_hour INTEGER NOT NULL,
	end_hour INTEGER NOT NULL,
	show_full_day BOOLEAN NOT NULL,
	week_start TEXT DEFAULT 'monday' NOT NULL,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id)
);
CREATE TABLE days (
	id INTEGER NOT NULL,
	date DATE NOT NULL,
	start_hour INTEGER NOT NULL,
	end_hour INTEGER NOT NULL,
	show_full_day BOOLEAN NOT NULL,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_days_date UNIQUE (date)
);
CREATE TABLE projects (
	id INTEGER NOT NULL,
	name TEXT NOT NULL,
	description TEXT DEFAULT '' NOT NULL,
	deadline_date DATE,
	deadline_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	UNIQUE (name)
);
CREATE TABLE recurrence_occurrences (
	id INTEGER NOT NULL,
	template_id INTEGER NOT NULL,
	occurrence_key TEXT NOT NULL,
	cycle_start DATE NOT NULL,
	cycle_end DATE NOT NULL,
	task_id INTEGER,
	suppressed BOOLEAN DEFAULT 'false' NOT NULL,
	structurally_protected BOOLEAN DEFAULT 'false' NOT NULL,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_recurrence_occurrence_key UNIQUE (template_id, occurrence_key),
	FOREIGN KEY(template_id) REFERENCES recurring_templates (id) ON DELETE CASCADE,
	FOREIGN KEY(task_id) REFERENCES tasks (id) ON DELETE SET NULL
);
CREATE TABLE recurring_checklist_items (
	id INTEGER NOT NULL,
	template_id INTEGER NOT NULL,
	title TEXT NOT NULL,
	position INTEGER NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY(template_id) REFERENCES recurring_templates (id) ON DELETE CASCADE
);
CREATE TABLE recurring_templates (
	id INTEGER NOT NULL,
	project_id INTEGER,
	task_type_id INTEGER,
	title TEXT NOT NULL,
	description TEXT DEFAULT '' NOT NULL,
	mode VARCHAR(16) NOT NULL,
	status VARCHAR(16) DEFAULT 'active' NOT NULL,
	frequency VARCHAR(16) NOT NULL,
	interval INTEGER DEFAULT '1' NOT NULL,
	weekdays_json TEXT DEFAULT '[]' NOT NULL,
	month_day INTEGER,
	quota_count INTEGER,
	start_date DATE NOT NULL,
	generation_start_date DATE NOT NULL,
	end_date DATE,
	cycle_limit INTEGER,
	urgency VARCHAR(16),
	importance VARCHAR(16),
	paused_at DATETIME,
	ended_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY(project_id) REFERENCES projects (id) ON DELETE SET NULL,
	FOREIGN KEY(task_type_id) REFERENCES task_types (id) ON DELETE SET NULL
);
CREATE TABLE task_completion_operations (
	token TEXT NOT NULL,
	root_task_id INTEGER,
	snapshot_json TEXT NOT NULL,
	completed_task_version INTEGER,
	undone_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (token),
	FOREIGN KEY(root_task_id) REFERENCES tasks (id) ON DELETE SET NULL
);
CREATE TABLE task_types (
	id INTEGER NOT NULL,
	name TEXT NOT NULL,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id)
);
CREATE TABLE tasks (
	id INTEGER NOT NULL,
	parent_id INTEGER,
	project_id INTEGER,
	task_type_id INTEGER,
	recurring_template_id INTEGER,
	occurrence_key TEXT,
	recurrence_kind TEXT,
	quota_period_start DATE,
	quota_period_end DATE,
	expected_sessions INTEGER,
	session_index INTEGER,
	recurrence_overrides_json TEXT DEFAULT '[]' NOT NULL,
	title TEXT NOT NULL,
	description TEXT DEFAULT '' NOT NULL,
	ready_to_plan BOOLEAN DEFAULT 'false' NOT NULL,
	is_blocked BOOLEAN DEFAULT 'false' NOT NULL,
	blocking_reason TEXT,
	status VARCHAR(24) NOT NULL,
	checked BOOLEAN DEFAULT 'false' NOT NULL,
	completed_at DATETIME,
	version INTEGER DEFAULT '1' NOT NULL,
	last_non_completed_status VARCHAR(24),
	urgency VARCHAR(16),
	importance VARCHAR(16),
	deadline_date DATE,
	deadline_at DATETIME,
	reminder_at DATETIME,
	reminder_delivered_at DATETIME,
	position INTEGER DEFAULT '0' NOT NULL,
	archived_at DATETIME,
	deleted_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY(parent_id) REFERENCES tasks (id) ON DELETE CASCADE,
	FOREIGN KEY(project_id) REFERENCES projects (id) ON DELETE CASCADE,
	FOREIGN KEY(task_type_id) REFERENCES task_types (id) ON DELETE SET NULL,
	FOREIGN KEY(recurring_template_id) REFERENCES recurring_templates (id) ON DELETE SET NULL
);
CREATE TABLE time_blocks (
	id INTEGER NOT NULL,
	day_id INTEGER,
	lane VARCHAR(16) NOT NULL,
	task_type_id INTEGER NOT NULL,
	task_id INTEGER,
	note TEXT,
	planned_block_id INTEGER,
	start_minute INTEGER,
	end_minute INTEGER,
	start_at DATETIME,
	end_at DATETIME,
	created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uq_time_blocks_planned_actual_correspondence UNIQUE (planned_block_id),
	CONSTRAINT ck_time_blocks_correspondence_from_actual CHECK (planned_block_id IS NULL OR lane = 'actual'),
	CONSTRAINT ck_time_blocks_actual_end_requires_start CHECK (end_at IS NULL OR start_at IS NOT NULL),
	CONSTRAINT ck_time_blocks_planned_grid_shape CHECK (lane != 'planned' OR (day_id IS NOT NULL AND start_minute IS NOT NULL AND end_minute IS NOT NULL AND start_at IS NULL AND end_at IS NULL)),
	CONSTRAINT ck_time_blocks_definitive_actual_shape CHECK (lane != 'actual' OR start_at IS NULL OR (day_id IS NULL AND start_minute IS NULL AND end_minute IS NULL)),
	CONSTRAINT ck_time_blocks_actual_positive_interval CHECK (lane != 'actual' OR start_at IS NULL OR end_at IS NULL OR end_at > start_at),
	FOREIGN KEY(day_id) REFERENCES days (id) ON DELETE CASCADE,
	FOREIGN KEY(task_type_id) REFERENCES task_types (id) ON DELETE RESTRICT,
	FOREIGN KEY(task_id) REFERENCES tasks (id) ON DELETE SET NULL,
	FOREIGN KEY(planned_block_id) REFERENCES time_blocks (id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX ix_actual_block_record_operations_actual_block_id ON actual_block_record_operations (actual_block_id);
CREATE INDEX ix_actual_block_record_operations_planned_block_id ON actual_block_record_operations (planned_block_id);
CREATE INDEX ix_days_date ON days (date);
CREATE INDEX ix_recurrence_occurrences_task_id ON recurrence_occurrences (task_id);
CREATE INDEX ix_recurrence_occurrences_template_id ON recurrence_occurrences (template_id);
CREATE INDEX ix_recurring_checklist_items_template_id ON recurring_checklist_items (template_id);
CREATE INDEX ix_recurring_templates_project_id ON recurring_templates (project_id);
CREATE INDEX ix_recurring_templates_task_type_id ON recurring_templates (task_type_id);
CREATE INDEX ix_task_completion_operations_root_task_id ON task_completion_operations (root_task_id);
CREATE INDEX ix_tasks_parent_id ON tasks (parent_id);
CREATE INDEX ix_tasks_project_id ON tasks (project_id);
CREATE INDEX ix_tasks_recurring_template_id ON tasks (recurring_template_id);
CREATE INDEX ix_tasks_task_type_id ON tasks (task_type_id);
CREATE INDEX ix_time_blocks_day_id ON time_blocks (day_id);
CREATE INDEX ix_time_blocks_planned_block_id ON time_blocks (planned_block_id);
CREATE INDEX ix_time_blocks_task_id ON time_blocks (task_id);
CREATE INDEX ix_time_blocks_task_type_id ON time_blocks (task_type_id);
CREATE UNIQUE INDEX uq_recurrence_occurrences_task_id ON recurrence_occurrences (task_id);
CREATE UNIQUE INDEX uq_time_blocks_one_active_actual ON time_blocks (lane) WHERE lane = 'actual' AND start_at IS NOT NULL AND end_at IS NULL;
CREATE TRIGGER validate_time_block_actual_insert
    BEFORE INSERT ON time_blocks
    WHEN NEW.lane = 'actual'
      AND (NEW.start_at IS NOT NULL OR NEW.planned_block_id IS NOT NULL)
    BEGIN
      SELECT CASE WHEN NEW.planned_block_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM time_blocks planned
        WHERE planned.id = NEW.planned_block_id AND planned.lane = 'planned'
      ) THEN RAISE(ABORT, 'Actual correspondence must target a Planned Block') END;
      SELECT CASE WHEN EXISTS (
        SELECT 1 FROM time_blocks actual
        WHERE actual.lane = 'actual' AND actual.start_at IS NOT NULL
          AND NEW.start_at < COALESCE(actual.end_at, '9999-12-31 23:59:59')
          AND actual.start_at < COALESCE(NEW.end_at, '9999-12-31 23:59:59')
      ) THEN RAISE(ABORT, 'Actual Blocks cannot overlap') END;
    END;
CREATE TRIGGER validate_time_block_actual_update
    BEFORE UPDATE OF lane, planned_block_id, start_at, end_at ON time_blocks
    WHEN NEW.lane = 'actual'
      AND (NEW.start_at IS NOT NULL OR NEW.planned_block_id IS NOT NULL)
    BEGIN
      SELECT CASE WHEN NEW.planned_block_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM time_blocks planned
        WHERE planned.id = NEW.planned_block_id AND planned.lane = 'planned'
      ) THEN RAISE(ABORT, 'Actual correspondence must target a Planned Block') END;
      SELECT CASE WHEN EXISTS (
        SELECT 1 FROM time_blocks actual
        WHERE actual.id != NEW.id AND actual.lane = 'actual' AND actual.start_at IS NOT NULL
          AND NEW.start_at < COALESCE(actual.end_at, '9999-12-31 23:59:59')
          AND actual.start_at < COALESCE(NEW.end_at, '9999-12-31 23:59:59')
      ) THEN RAISE(ABORT, 'Actual Blocks cannot overlap') END;
    END;
