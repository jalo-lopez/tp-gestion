/**
 * Cliente API y utilidades comunes en Vanilla JS para interactuar con Spring Boot BPM.
 */
const API_BASE_URL = window.location.origin.includes(':8080')
  ? `${window.location.origin}/api/v1/cursos/procesos`
  : 'http://localhost:8080/api/v1/cursos/procesos';

/**
 * Muestra una notificación emergente (Toast)
 */
function showToast(mensaje, tipo = 'success') {
  let container = document.querySelector('.toast-container');
  if (!container) {
    container = document.createElement('div');
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast--${tipo}`;
  
  const icon = tipo === 'success' ? '✓' : (tipo === 'error' ? '✕' : 'ℹ');
  toast.innerHTML = `<strong>${icon}</strong> <span>${mensaje}</span>`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(50px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

/**
 * Cliente HTTP estandarizado
 */
const api = {
  // Iniciar proceso desde formulario web
  iniciarProceso: async (payload) => {
    const res = await fetch(`${API_BASE_URL}/iniciar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'Error al iniciar el proceso');
    return json.datos;
  },

  // Consultar estado de curso/proceso para empresas
  consultarEstado: async (identificador) => {
    const res = await fetch(`${API_BASE_URL}/cursos/${encodeURIComponent(identificador)}/estado`);
    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'No se encontró el curso solicitado');
    return json.datos;
  },

  // Obtener tareas pendientes por usuario administrativo
  obtenerTareasPendientes: async (usuario) => {
    const res = await fetch(`${API_BASE_URL}/tareas/pendientes?usuario=${encodeURIComponent(usuario)}`);
    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'Error al obtener tareas pendientes');
    return json.datos || [];
  },

  // Reclamar tarea (claim)
  reclamarTarea: async (taskId, usuario) => {
    const res = await fetch(`${API_BASE_URL}/tareas/${encodeURIComponent(taskId)}/reclamar?usuario=${encodeURIComponent(usuario)}`, {
      method: 'POST'
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'Error al reclamar la tarea');
    return json;
  },

  // Completar tarea con decisión
  completarTarea: async (taskId, payload) => {
    const res = await fetch(`${API_BASE_URL}/tareas/${encodeURIComponent(taskId)}/completar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.mensaje || 'Error al completar la tarea');
    return json;
  }
};
