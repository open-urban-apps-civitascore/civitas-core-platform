import Image from 'next/image'

const Page = async () => {
  return (
    <div className="flex flex-1 flex-col">
      <div className="grid auto-rows-min gap-4 md:grid-cols-3">
        <div className="bg-muted/50 aspect-video rounded-xl" />
        <div className="bg-muted/50 aspect-video rounded-xl" />
        <div className="bg-muted/50 aspect-video rounded-xl" />
        <Image src="/image.jpg" alt="image" width={100} height={5} />
      </div>
      <div className="bg-muted/50 min-h-[50vh] flex-1 rounded-xl" />
    </div>
  )
}

export default Page
