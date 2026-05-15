'use strict';

// -------------------------------------------------------
// Configuration
// -------------------------------------------------------

const API_BASE = '/api/tasks';

const STATUSES       = ['TODO', 'IN_PROGRESS', 'DONE'];
const STATUS_LABELS  = { TODO: 'To Do', IN_PROGRESS: 'In Progress', DONE: 'Done' };
const STATUS_NEXT    = { TODO: 'IN_PROGRESS', IN_PROGRESS: 'DONE',  DONE: null };
const STATUS_PREV    = { TODO: null,           IN_PROGRESS: 'TODO',  DONE: 'IN_PROGRESS' };

// In-memory cache so move actions don't need an extra GET
let tasksCache = {};

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

function loadTasks()              { return apiFetch(API_BASE); }
function createTask(title, desc)  {
    return apiFetch(API_BASE, {
        method: 'POST',
        body: JSON.stringify({ title, description: desc, status: 'TODO' }),
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
    // Rebuild cache
    tasksCache = Object.fromEntries(tasks.map(t => [t.id, t]));

    STATUSES.forEach(status => {
        const list    = document.getElementById(`list-${status}`);
        const countEl = document.getElementById(`count-${status}`);
        const subset  = tasks.filter(t => t.status === status);

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

    const prev = STATUS_PREV[task.status];
    const next = STATUS_NEXT[task.status];

    card.innerHTML = `
        <div class="card-header">
            <span class="card-title">${escapeHtml(task.title)}</span>
            <button class="btn-delete"
                    data-id="${task.id}"
                    title="Delete task"
                    aria-label="Delete task">&#x2715;</button>
        </div>
        ${task.description ? `<p class="card-desc">${escapeHtml(task.description)}</p>` : ''}
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
// Board event delegation (delete + move)
// -------------------------------------------------------

document.getElementById('board').addEventListener('click', async e => {
    const delBtn  = e.target.closest('.btn-delete');
    const moveBtn = e.target.closest('.btn-move');

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

const modal    = document.getElementById('task-modal');
const openBtn  = document.getElementById('btn-add-task');
const closeBtn = document.getElementById('btn-close-modal');
const cancelBtn = document.getElementById('btn-cancel');
const form     = document.getElementById('new-task-form');
const titleInput = document.getElementById('task-title');

function openModal() {
    modal.hidden = false;
    titleInput.focus();
}

function closeModal() {
    modal.hidden = true;
    form.reset();
    titleInput.classList.remove('invalid');
    clearError();
}

openBtn.addEventListener('click', openModal);
closeBtn.addEventListener('click', closeModal);
cancelBtn.addEventListener('click', closeModal);

// Close on backdrop click
modal.addEventListener('click', e => {
    if (e.target === modal) closeModal();
});

// Close on Escape key
document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && !modal.hidden) closeModal();
});

// -------------------------------------------------------
// New task form submission
// -------------------------------------------------------

form.addEventListener('submit', async e => {
    e.preventDefault();

    const title = titleInput.value.trim();
    const desc  = document.getElementById('task-desc').value.trim();

    if (!title) {
        titleInput.classList.add('invalid');
        titleInput.focus();
        return;
    }

    titleInput.classList.remove('invalid');
    const submitBtn = form.querySelector('[type="submit"]');
    submitBtn.disabled = true;

    try {
        await createTask(title, desc);
        closeModal();
        await refresh();
    } catch (err) {
        showError('Failed to create task. Please try again.');
        console.error(err);
    } finally {
        submitBtn.disabled = false;
    }
});

// -------------------------------------------------------
// Bootstrap
// -------------------------------------------------------

document.addEventListener('DOMContentLoaded', refresh);
