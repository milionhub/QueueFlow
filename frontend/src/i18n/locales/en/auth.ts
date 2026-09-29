/** Sign-in, registration and the session screens. */
export const auth = {
  brand: {
    tagline: 'Plan. Build. Ship.',
    promise: 'A focused workspace for software teams to move work forward, from backlog to done.',
    about: 'About QueueFlow',
    features: {
      boards: 'Boards and lists',
      conversations: 'Conversations on every ticket',
      history: 'A full history of changes',
    },
    // The made-up tickets of the brand panel's picture: nothing here is real data.
    sample: {
      pricing: 'Pricing table refresh',
      rateLimit: 'Rate-limit sign-in',
      footer: 'Footer links',
      onboarding: 'Onboarding checklist',
      sso: 'SSO for workspaces',
      emptyStates: 'Empty states',
      projectKeys: 'Project keys',
    },
  },
  fields: {
    name: 'Name',
    yourName: 'Your name',
    email: 'Email',
    password: 'Password',
    workspaceName: 'Workspace name',
    workspacePlaceholder: 'e.g. Acme Engineering',
  },
  passwordHint:
    'At least 8 characters. Very long passwords are limited to 72 bytes (fewer characters if they include accents or emoji).',
  login: {
    documentTitle: 'Sign in',
    title: 'Sign in',
    description: 'Welcome back. Sign in to your QueueFlow workspace.',
    newHere: 'New to QueueFlow?',
    createWorkspace: 'Create a workspace',
    sessionEnded: 'Your session has ended. Please sign in again.',
    enterEmail: 'Enter your email.',
    enterPassword: 'Enter your password.',
    invalidCredentials: 'Invalid email or password.',
    submit: 'Sign in',
    submitting: 'Signing in…',
  },
  register: {
    documentTitle: 'Create workspace',
    title: 'Create your workspace',
    description: "Set up a new QueueFlow workspace. You'll be its admin and can add your teammates once you're in.",
    haveAccount: 'Already have an account?',
    signIn: 'Sign in',
    required: {
      name: 'Enter your name.',
      email: 'Enter your email.',
      password: 'Enter a password.',
      workspaceName: 'Enter a name for your workspace.',
    },
    emailTaken: 'An account with that email already exists. Sign in instead?',
    submit: 'Create workspace',
    submitting: 'Creating workspace…',
  },
  session: {
    loading: 'Loading QueueFlow…',
    documentTitle: 'Connection problem',
    unavailableTitle: "We couldn't reach QueueFlow",
    unavailableBody:
      "The server isn't responding right now, so your session couldn't be checked. You're still signed in; try again in a moment.",
    tryAgain: 'Try again',
    signOut: 'Sign out',
  },
}
