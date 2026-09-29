import type { auth as en } from '../en/auth'
import type { Messages } from '../types'

export const auth: Messages<typeof en> = {
  brand: {
    tagline: 'Planifica. Crea. Entrega.',
    promise: 'Un espacio de trabajo pensado para que los equipos de software hagan avanzar su trabajo, del backlog a hecho.',
    about: 'Sobre QueueFlow',
    features: {
      boards: 'Tableros y listas',
      conversations: 'Conversaciones en cada ticket',
      history: 'Un historial completo de cambios',
    },
    sample: {
      pricing: 'Renovar la tabla de precios',
      rateLimit: 'Limitar intentos de inicio de sesión',
      footer: 'Enlaces del pie de página',
      onboarding: 'Lista de bienvenida',
      sso: 'SSO para espacios de trabajo',
      emptyStates: 'Estados vacíos',
      projectKeys: 'Claves de proyecto',
    },
  },
  fields: {
    name: 'Nombre',
    yourName: 'Tu nombre',
    email: 'Correo electrónico',
    password: 'Contraseña',
    workspaceName: 'Nombre del espacio de trabajo',
    workspacePlaceholder: 'p. ej., Acme Ingeniería',
  },
  passwordHint:
    'Al menos 8 caracteres. Las contraseñas muy largas se limitan a 72 bytes (menos caracteres si incluyen acentos o emojis).',
  login: {
    documentTitle: 'Iniciar sesión',
    title: 'Iniciar sesión',
    description: 'Hola de nuevo. Inicia sesión en tu espacio de trabajo de QueueFlow.',
    newHere: '¿Primera vez en QueueFlow?',
    createWorkspace: 'Crea un espacio de trabajo',
    sessionEnded: 'Tu sesión ha terminado. Vuelve a iniciar sesión.',
    enterEmail: 'Introduce tu correo electrónico.',
    enterPassword: 'Introduce tu contraseña.',
    invalidCredentials: 'Correo electrónico o contraseña incorrectos.',
    submit: 'Iniciar sesión',
    submitting: 'Iniciando sesión…',
  },
  register: {
    documentTitle: 'Crear espacio de trabajo',
    title: 'Crea tu espacio de trabajo',
    description:
      'Configura un nuevo espacio de trabajo de QueueFlow. Tendrás el rol de admin y podrás añadir a tu equipo en cuanto entres.',
    haveAccount: '¿Ya tienes una cuenta?',
    signIn: 'Inicia sesión',
    required: {
      name: 'Introduce tu nombre.',
      email: 'Introduce tu correo electrónico.',
      password: 'Introduce una contraseña.',
      workspaceName: 'Introduce un nombre para tu espacio de trabajo.',
    },
    emailTaken: 'Ya existe una cuenta con ese correo electrónico. ¿Quieres iniciar sesión?',
    submit: 'Crear espacio de trabajo',
    submitting: 'Creando espacio de trabajo…',
  },
  session: {
    loading: 'Cargando QueueFlow…',
    documentTitle: 'Problema de conexión',
    unavailableTitle: 'No hemos podido conectar con QueueFlow',
    unavailableBody:
      'El servidor no responde en este momento, así que no se ha podido comprobar tu sesión. Sigues con la sesión iniciada; vuelve a intentarlo en un momento.',
    tryAgain: 'Volver a intentarlo',
    signOut: 'Cerrar sesión',
  },
}
