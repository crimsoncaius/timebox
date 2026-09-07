import original from '../../frontend/playwright.config'
import {fileURLToPath} from 'node:url'
const snapshot=fileURLToPath(new URL('./baseline-ebf691e/',import.meta.url));
export default {
  ...original,
  testDir:snapshot+'frontend/e2e',
  outputDir:fileURLToPath(new URL('./baseline-e2e-results',import.meta.url)),
  reporter:'list',
  webServer:(original.webServer as Array<Record<string,unknown>>).map((server,index)=>({
    ...server,
    reuseExistingServer:false,
    cwd:snapshot+(index===0?'backend':'frontend'),
    ...(index===0?{
      command:'"C:/Users/Caius/Desktop/timebox/backend/.venv/Scripts/python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 18001',
      env:{...(server.env as Record<string,string>),DATABASE_URL:'sqlite:///C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/baseline-e2e.sqlite'},
    }:{}),
  })),
}
