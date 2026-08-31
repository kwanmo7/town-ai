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

/** Local 비인증 개발과 Production Firebase 인증 흐름을 전환하는 Build 설정이다. */
export const isWebAuthEnabled = import.meta.env.VITE_WEB_AUTH_ENABLED === 'true'

const firebaseApp = initializeApp(firebaseConfig)

/** Google 로그인과 ID Token 갱신에 사용하는 Firebase Auth Instance이다. */
export const firebaseAuth = getAuth(firebaseApp)

/** 현재 Firebase 사용자의 최신 ID Token을 반환하며 비인증 개발에서는 null을 반환한다. */
export async function getFirebaseIdToken(): Promise<string | null> {
  if (!isWebAuthEnabled || firebaseAuth.currentUser === null) {
    return null
  }
  return firebaseAuth.currentUser.getIdToken()
}
