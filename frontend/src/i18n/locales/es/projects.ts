import type { projects as en } from '../en/projects'
import type { Messages } from '../types'

export const projects: Messages<typeof en> = {
  title: 'Proyectos',
  count_one: '{{count}} proyecto',
  count_other: '{{count}} proyectos',
  newProject: 'Nuevo proyecto',
  loading: 'Cargando proyectos…',
  loadError: 'No se han podido cargar los proyectos.',
  created: 'Proyecto {{key}} creado.',
  updated: 'Proyecto {{key}} actualizado.',
  intro:
    'Los proyectos agrupan los tickets de tu equipo. Cada uno tiene una clave corta, como <key>CORE</key>, que se antepone a los identificadores de sus tickets (<id>CORE-7</id>).',
  empty: {
    title: 'Todavía no hay proyectos',
    memberNote: 'Un admin del espacio de trabajo puede crear el primer proyecto.',
  },
  editProject: 'Editar {{key}}',
  views: {
    label: 'Vistas del proyecto',
    list: 'Lista',
    board: 'Tablero',
  },
  project: {
    loading: 'Cargando el proyecto…',
    loadError: 'No se ha podido cargar el proyecto.',
    notFoundTitle: 'Proyecto no encontrado',
    notFoundBody: 'No hay ningún proyecto con esta clave en tu espacio de trabajo.',
    backToProjects: 'Volver a Proyectos',
  },
  form: {
    createTitle: 'Nuevo proyecto',
    editTitle: 'Editar {{key}}',
    createDescription: 'Los proyectos agrupan tus tickets. Cada proyecto tiene una clave corta y permanente.',
    editDescription: 'Actualiza el nombre o la descripción de {{name}}.',
    name: 'Nombre',
    namePlaceholder: 'p. ej., Plataforma principal',
    key: 'Clave',
    keyPlaceholder: 'CORE',
    keyHint:
      'De 2 a 10 letras o dígitos, p. ej., CORE. Se antepone a los identificadores de los tickets (CORE-7) y no se puede cambiar después.',
    keyFixed: 'La clave no se puede cambiar porque forma parte del identificador de cada ticket.',
    description: 'Descripción',
    optional: 'Opcional.',
    enterName: 'Introduce un nombre para el proyecto.',
    nameTooLong: 'El nombre no puede tener más de {{max}} caracteres.',
    enterKey: 'Introduce una clave para el proyecto.',
    keyFormat: 'Usa de 2 a 10 letras o dígitos, p. ej., CORE.',
    create: 'Crear proyecto',
    creating: 'Creando…',
  },
  errors: {
    keyTaken: 'Ya existe un proyecto con la clave {{key}}.',
    keyTakenUnknown: 'Ya existe un proyecto con esta clave.',
    forbiddenCreate: 'Solo los admins del espacio de trabajo pueden crear proyectos.',
    forbiddenEdit: 'Solo los admins del espacio de trabajo pueden editar proyectos.',
    gone: 'Este proyecto ya no está disponible.',
  },
}
