import type { members as en } from '../en/members'
import type { Messages } from '../types'

export const members: Messages<typeof en> = {
  title: 'Miembros',
  count_one: '{{count}} miembro',
  count_other: '{{count}} miembros',
  roleNote:
    'Los admins pueden crear y editar proyectos, y añadir, editar y eliminar miembros. Todo el mundo puede trabajar con tickets, el tablero, las etiquetas y los comentarios.',
  addMember: 'Añadir miembro',
  loading: 'Cargando miembros…',
  loadError: 'No se han podido cargar los miembros.',
  empty: 'No se han encontrado miembros.',
  added:
    'Se ha añadido a {{name}} como miembro. Comparte su correo electrónico y la contraseña que has elegido por un canal de confianza.',
  removedToast: 'Se ha eliminado a {{name}} del espacio de trabajo.',
  list: {
    label: 'Miembros del espacio de trabajo',
    emailPrefix: 'Correo electrónico: ',
  },
  actions: {
    trigger: 'Acciones para {{name}}',
    tooltip: 'Acciones del miembro',
    edit: 'Editar miembro',
    remove: 'Eliminar miembro',
  },
  fields: {
    name: 'Nombre',
    email: 'Correo electrónico',
    password: 'Contraseña',
    confirmPassword: 'Confirmar contraseña',
  },
  validation: {
    enterName: 'Introduce un nombre.',
    nameTooLong: 'El nombre no puede tener más de {{max}} caracteres.',
    enterEmail: 'Introduce una dirección de correo electrónico.',
    invalidEmail: 'Introduce una dirección de correo electrónico válida, p. ej., nombre@ejemplo.com.',
    enterPassword: 'Introduce una contraseña.',
    passwordTooShort: 'La contraseña debe tener al menos {{min}} caracteres.',
    passwordTooLong:
      'La contraseña no puede ocupar más de {{max}} bytes: usa menos caracteres, o menos acentos y emojis.',
    confirmPassword: 'Vuelve a introducir la contraseña.',
    passwordsDiffer: 'Las contraseñas no coinciden.',
  },
  add: {
    description: 'Crea una cuenta para alguien de tu equipo. Se unirá a este espacio de trabajo como miembro.',
    emailHint: 'Lo usará para iniciar sesión.',
    signInDetails: 'Datos de acceso',
    note: 'QueueFlow no envía correos electrónicos. Comparte el correo y la contraseña directamente con esa persona, por un canal de confianza. La contraseña no se podrá volver a mostrar ni cambiar después en QueueFlow.',
    submit: 'Añadir miembro',
    submitting: 'Añadiendo…',
    emailTaken: 'Ese correo electrónico ya lo usa una cuenta de QueueFlow.',
    forbidden: 'Solo los admins del espacio de trabajo pueden añadir miembros.',
    workspaceGone:
      'No se ha podido añadir al miembro: este espacio de trabajo no está disponible. Recarga la página y vuelve a intentarlo.',
  },
  edit: {
    title: 'Editar miembro',
    description: 'Cambia cómo aparece el nombre de este miembro en todo el espacio de trabajo.',
    nameHint: 'Su correo electrónico y su contraseña no cambian.',
    checkName: 'Revisa el nombre y vuelve a intentarlo.',
    forbidden: 'Solo los admins del espacio de trabajo pueden editar miembros.',
  },
  remove: {
    title: 'Eliminar miembro',
    description: 'Perderá el acceso a este espacio de trabajo de inmediato.',
    signIn: 'Ya no podrá iniciar sesión, y cualquier sesión que tenga abierta dejará de funcionar.',
    unassigned: 'Los tickets que tenga asignados quedarán sin asignar.',
    history: 'Los tickets que creó, sus comentarios y su actividad se conservan, con su nombre.',
    undoStrong: 'No se puede deshacer.',
    undoRest: 'Para volver a darle acceso, añade una cuenta de miembro nueva.',
    submit: 'Eliminar miembro',
    submitting: 'Eliminando…',
    forbidden: 'Solo los admins del espacio de trabajo pueden eliminar miembros.',
    cannotRemoveSelf: 'No puedes eliminar tu propia cuenta del espacio de trabajo.',
    cannotRemoveAdmin: 'Solo se pueden eliminar miembros, no admins.',
    cannotRemove: 'Este miembro no se puede eliminar.',
  },
  gone: 'Este miembro ya no forma parte del espacio de trabajo.',
}
