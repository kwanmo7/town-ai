import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'

const firebaseConfig = {
  apiKey: 'AIzaSyDJ8keZW8AlbXgIcGSbRhcx7_7dmwFrZbI',
  authDomain: 'town-ai.firebaseapp.com',
  projectId: 'town-ai',
  storageBucket: 'town-ai.firebasestorage.app',
  messagingSenderId: '574086886148',
  appId: '1:574086886148:web:10adc9126b11640777d651',
}

export const isWebAuthEnabled = import.meta.env.VITE_WEB_AUTH_ENABLED === 'true'

const firebaseApp = initializeApp(firebaseConfig)

export const firebaseAuth = getAuth(firebaseApp)

export async function getFirebaseIdToken(): Promise<string | null> {
  if (!isWebAuthEnabled || firebaseAuth.currentUser === null) {
    return null
  }
  return firebaseAuth.currentUser.getIdToken()
}
