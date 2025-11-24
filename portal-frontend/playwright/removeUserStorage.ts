import fs from 'fs'

const authFile = './.auth/user.json'

const removeUserStorage = async () => {
  if (fs.existsSync(authFile)) {
    fs.unlinkSync(authFile)
    console.log('User storage removed')
  }
}

export default removeUserStorage
