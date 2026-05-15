'use strict';

// -------------------------------------------------------
// Configuration
// -------------------------------------------------------

const API_BASE = '/api/tasks';

const STATUSES       = ['TODO', 'IN_PROGRESS', 'DONE'];
const STATUS_LABELS  = { TODO: 'To Do', IN_PROGRESS: 'In Progress', DONE: 'Done' };
const STATUS_NEXT    = { TODO: 'IN_PROGRESS', IN_PROGRESS: 'DONE',  DONE: null };
const STATUS_PREV    = { TODO: null,           IN_PROGRESS: 'TODO',  DONE: 'IN_PROGRESS' };

const PRIORITY_LABELS = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' };
const PRIORITY_CLASS  = { LOW: 'priority-low', MEDIUM: 'priority-medium', HIGH: 'priority-high' };
const PRIORITY_EMOJI  = { LOW: '🟢', MEDIUM: '🟡', HIGH: '🔴' };

// In-memory cache so move actions don't need an extra GET
let tasksCache = {};

// Edit-mode state
let editingTaskId = null;

// Live search state
let searchQuery = '';

// -------------------------------------------------------
// API helpers
// -------------------------------------------------------

async function apiFetch(url, options = {}) {
    const res = await fetch(url, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
    });

    if (!res.ok) {
        const text = await res.text().catch(() => '');
        throw new Error(`Request failed [${res.status}]: ${text}`);
    }

    // 204 No Content
    if (res.status === 204) return null;
    return res.json();
}

function loadTasks() { return apiFetch(API_BASE); }

function createTask(title, desc, priority) {
    return apiFetch(API_BASE, {
        method: 'POST',
        body: JSON.stringify({ title, description: desc, status: 'TODO', priority }),
    });
}

function saveTask(id, data) {
    return apiFetch(`${API_BASE}/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
    });
}

function moveTask(id, newStatus) {
    const task = { ...tasksCache[id], status: newStatus };
    return apiFetch(`${API_BASE}/${id}`, {
        method: 'PUT',
        body: JSON.stringify(task),
    });
}

function deleteTask(id) {
    return apiFetch(`${API_BASE}/${id}`, { method: 'DELETE' });
}

// -------------------------------------------------------
// Rendering
// -------------------------------------------------------

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str));
    return div.innerHTML;
}

function renderBoard(tasks) {
    // Rebuild cache (unfiltered)
    tasksCache = Object.fromEntries(tasks.map(t => [t.id, t]));

    // Apply search filter
    const q = searchQuery.toLowerCase();
    const filtered = q
        ? tasks.filter(t =>
            t.title.toLowerCase().includes(q) ||
            (t.description && t.description.toLowerCase().includes(q)))
        : tasks;

    STATUSES.forEach(status => {
        const list    = document.getElementById(`list-${status}`);
        const countEl = document.getElementById(`count-${status}`);
        const subset  = filtered.filter(t => t.status === status);

        countEl.textContent = subset.length;
        list.innerHTML = '';

        if (subset.length === 0) {
            list.innerHTML = '<p class="empty-msg">No tasks here yet</p>';
            return;
        }

        subset.forEach(task => list.appendChild(buildCard(task)));
    });
}

function buildCard(task) {
    const card  = document.createElement('div');
    card.className = 'task-card';

    const prev     = STATUS_PREV[task.status];
    const next     = STATUS_NEXT[task.status];
    const priority = task.priority || 'MEDIUM';

    card.innerHTML = `
        <div class="card-header">
            <span class="card-title">${escapeHtml(task.title)}</span>
            <div class="card-actions">
                <button class="btn-edit"
                        data-id="${task.id}"
                        title="Edit task"
                        aria-label="Edit task">&#9998;</button>
                <button class="btn-delete"
                        data-id="${task.id}"
                        title="Delete task"
                        aria-label="Delete task">&#x2715;</button>
            </div>
        </div>
        ${task.description ? `<p class="card-desc">${escapeHtml(task.description)}</p>` : ''}
        <div class="card-meta">
            <span class="priority-badge ${PRIORITY_CLASS[priority]}">
                ${PRIORITY_EMOJI[priority]} ${PRIORITY_LABELS[priority]}
            </span>
        </div>
        <div class="card-footer">
            ${prev
                ? `<button class="btn-move btn-prev"
                           data-id="${task.id}"
                           data-target="${prev}"
                           title="Move to ${STATUS_LABELS[prev]}">
                       &larr; ${STATUS_LABELS[prev]}
                   </button>`
                : '<span></span>'}
            ${next
                ? `<button class="btn-move btn-next"
                           data-id="${task.id}"
                           data-target="${next}"
                           title="Move to ${STATUS_LABELS[next]}">
                       ${STATUS_LABELS[next]} &rarr;
                   </button>`
                : ''}
        </div>`;

    return card;
}

// -------------------------------------------------------
// Error / loading state
// -------------------------------------------------------

function showError(msg) {
    const el = document.getElementById('error-banner');
    el.textContent = msg;
    el.hidden = false;
}

function clearError() {
    document.getElementById('error-banner').hidden = true;
}

// -------------------------------------------------------
// Refresh (load + render)
// -------------------------------------------------------

async function refresh() {
    try {
        const tasks = await loadTasks();
        renderBoard(tasks);
        clearError();
    } catch (err) {
        showError('Could not connect to the backend. Make sure the API is running.');
        console.error(err);
    }
}

// -------------------------------------------------------
// Board event delegation (edit + delete + move)
// -------------------------------------------------------

document.getElementById('board').addEventListener('click', async e => {
    const editBtn = e.target.closest('.btn-edit');
    const delBtn  = e.target.closest('.btn-delete');
    const moveBtn = e.target.closest('.btn-move');

    if (editBtn) {
        const { id } = editBtn.dataset;
        const task = tasksCache[id];
        if (task) openModal(task);
        return;
    }

    if (delBtn) {
        const { id } = delBtn.dataset;
        delBtn.disabled = true;
        try {
            await deleteTask(id);
            await refresh();
        } catch (err) {
            showError('Failed to delete task.');
            delBtn.disabled = false;
        }
        return;
    }

    if (moveBtn) {
        const { id, target } = moveBtn.dataset;
        moveBtn.disabled = true;
        try {
            await moveTask(id, target);
            await refresh();
        } catch (err) {
            showError('Failed to move task.');
            moveBtn.disabled = false;
        }
    }
});

// -------------------------------------------------------
// Modal
// -------------------------------------------------------

const modal        = document.getElementById('task-modal');
const openBtn      = document.getElementById('btn-add-task');
const closeBtn     = document.getElementById('btn-close-modal');
const cancelBtn    = document.getElementById('btn-cancel');
const form         = document.getElementById('new-task-form');
const titleInput   = document.getElementById('task-title');
const descInput    = document.getElementById('task-desc');
const prioritySel  = document.getElementById('task-priority');
const modalTitle   = document.getElementById('modal-title');
const submitBtn    = document.getElementById('btn-submit');

/**
 * Open modal.
 * @param {object|null} task — pass a task object to enter edit mode,
 *                             or omit / pass null to create a new task.
 */
function openModal(task = null) {
    form.reset();
    titleInput.classList.remove('invalid');
    clearError();

    if (task) {
        // Edit mode
        editingTaskId         = task.id;
        titleInput.value      = task.title || '';
        descInput.value       = task.description || '';
        prioritySel.value     = task.priority || 'MEDIUM';
        modalTitle.textContent = 'Edit Task';
        submitBtn.textContent  = 'Save Changes';
    } else {
        // Create mode
        editingTaskId          = null;
        prioritySel.value      = 'MEDIUM';
        modalTitle.textContent = 'New Task';
        submitBtn.textContent  = 'Create Task';
    }

    modal.classList.add('is-open');
    modal.removeAttribute('hidden');
    titleInput.focus();
}

function closeModal() {
    modal.classList.remove('is-open');
    modal.setAttribute('hidden', '');
    form.reset();
    titleInput.classList.remove('invalid');
    editingTaskId = null;
    clearError();
}

openBtn.addEventListener('click', () => openModal());
closeBtn.addEventListener('click', closeModal);
cancelBtn.addEventListener('click', closeModal);

// Close on backdrop click
modal.addEventListener('click', e => {
    if (e.target === modal) closeModal();
});

// Close on Escape key
document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && modal.classList.contains('is-open')) closeModal();
});

// -------------------------------------------------------
// Form submission (create or update)
// -------------------------------------------------------

form.addEventListener('submit', async e => {
    e.preventDefault();

    const title    = titleInput.value.trim();
    const desc     = descInput.value.trim();
    const priority = prioritySel.value;

    if (!title) {
        titleInput.classList.add('invalid');
        titleInput.focus();
        return;
    }

    titleInput.classList.remove('invalid');
    submitBtn.disabled = true;

    try {
        if (editingTaskId !== null) {
            // Update existing task — preserve status from cache
            const existing = tasksCache[editingTaskId];
            await saveTask(editingTaskId, {
                title,
                description: desc,
                status: existing ? existing.status : 'TODO',
                priority,
            });
        } else {
            await createTask(title, desc, priority);
        }
        closeModal();
        await refresh();
    } catch (err) {
        showError(editingTaskId !== null
            ? 'Failed to update task. Please try again.'
            : 'Failed to create task. Please try again.');
        console.error(err);
    } finally {
        submitBtn.disabled = false;
    }
});

// -------------------------------------------------------
// Live search
// -------------------------------------------------------

document.getElementById('search-input').addEventListener('input', e => {
    searchQuery = e.target.value.trim();
    // Re-render with the cached task list filtered by the new query
    renderBoard(Object.values(tasksCache));
});

// -------------------------------------------------------
// Bootstrap
// -------------------------------------------------------

document.addEventListener('DOMContentLoaded', refresh);
